#!/usr/bin/env python3
"""Focused two-real-client Forge fixture. Prepare launch.json under the shared Forge lock first.
Run this controller under that same full-lifetime fcntl.lockf lease; it never invokes Gradle.
No synthetic pointer input. Screenshots come from Minecraft's native framebuffer callback.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time
import uuid
import zipfile


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--launch", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--provider", type=Path, required=True)
    parser.add_argument("--native-library-dir", type=Path, required=True,
                        help="Explicit inventoried Flite SDK library directory; audio playback is not tested")
    args = parser.parse_args()
    root = args.output.resolve()
    root.mkdir(parents=True, exist_ok=False)
    launch = json.loads(args.launch.read_text())
    processes = []
    handles = []
    findings = []
    captures = []
    summary = {"schema": "bc.celestial_font_visual.v1", "status": "running", "software_rendering": True,
               "encounter_acceptance": False, "launch_sha256": digest(args.launch), "provider": str(args.provider.resolve()),
               "provider_sha256": digest(args.provider), "captures": captures, "findings": findings}
    server = None

    def text(path):
        return path.read_text(errors="replace") if path.exists() else ""

    def start(label, command, cwd, environment=None, stdin=False):
        log = root / (label + ".log")
        handle = log.open("w")
        handles.append(handle)
        env = os.environ.copy()
        env.update(environment or {})
        env.update({"TMPDIR": str(root / "tmp"), "TMP": str(root / "tmp"), "TEMP": str(root / "tmp")})
        process = subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.PIPE if stdin else subprocess.DEVNULL,
                                   stdout=handle, stderr=subprocess.STDOUT, text=True, start_new_session=True)
        processes.append((label, process, log))
        return process, log

    def wait_for(path, pattern, timeout=90, offset=0):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            match = re.search(pattern, text(path)[offset:])
            if match:
                return match
            dead = [(label, process.returncode) for label, process, _ in processes if process.poll() is not None]
            if dead:
                raise RuntimeError("Owned runtime exited during observation: " + repr(dead))
            time.sleep(0.25)
        raise TimeoutError(f"Observation timed out after {timeout}s: {path.name}: {pattern}")

    def command(value):
        marker = "CELESTIAL_DONE_" + str(time.monotonic_ns())
        offset = len(text(server_log))
        server.stdin.write(value + "\nsay " + marker + "\n")
        server.stdin.flush()
        wait_for(server_log, re.escape(marker), 90, offset)

    def set_arg(command_line, name, value):
        if name in command_line:
            command_line[command_line.index(name) + 1] = value
        else:
            command_line += [name, value]

    def java_command(spec, memory):
        command_line = [str(value) for value in spec["command"]]
        command_line = [value for value in command_line if not value.startswith(("-Xmx", "-Xms", "-XX:ActiveProcessorCount="))]
        command_line[1:1] = ["-Xmx" + memory, "-Xms512M", "-XX:ActiveProcessorCount=4"]
        return command_line

    clients = []
    # A declared non-shipping definition override holds each reservoir state steady for both
    # native framebuffer captures. Production idle regeneration remains covered by the full gate.
    definition_source = Path(__file__).resolve().parents[1] / "src/main/resources/defaults/fonts/nether.json"
    definition = json.loads(definition_source.read_text())
    definition["passiveChargePerTick"] = 0.0
    summary["fixture_overrides"] = {"definition_source": str(definition_source),
        "definition_source_sha256": digest(definition_source), "passiveChargePerTick": 0.0}

    def prepare_definition(directory):
        (directory / "native-diagnostic-nonce").write_text(uuid.uuid4().hex)
        target = directory / "config/better_dimension_fonts/fonts/nether.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(definition, indent=2) + "\n")

    def capture(name, event, dimension="minecraft:overworld"):
        offset = len(text(server_log))
        command("celestial inspect")
        server_snapshot = wait_for(server_log,
            r"Celestial EventSnapshot\[event=(\w+), occurrenceId=(\d+), scheduledDay=(-?\d+), active=(\w+), remainingNightTicks=(\d+)\]", 30, offset)
        server_event, server_occurrence, _, server_active, server_remaining = server_snapshot.groups()
        if server_event != event or server_active != str(event != "NONE").lower():
            raise AssertionError("Server event mismatch: " + server_snapshot.group(0))
        observed = []
        for player, directory, log in clients:
            filename = name + "_" + player.lower()
            offset = len(text(log))
            command("fontvisual capture " + player + " " + filename)
            pattern = (r"CELESTIAL_VISUAL capture=" + re.escape(filename) + r"\.png player=" + player
                       + r" event=(\w+) active=(\w+) occurrence=(\d+) remaining=(\d+) dimension=([^\s]+)"
                       + r" resource=([^\s]+) resource_sha256=([a-f0-9]{64})"
                       + r" font_charge=(-?\d+) font_max=(-?\d+) font_ready=(\w+) font_free=(\w+) return_seen=(\w+) return_free=(\w+)")
            match = wait_for(log, pattern, 90, offset)
            (actual, active, occurrence, remaining, current_dimension, resource, resource_hash,
             font_charge, font_max, font_ready, font_free, return_seen, return_free) = match.groups()
            font_charge, font_max = int(font_charge), int(font_max)
            if name.startswith("font_"):
                free = name != "font_ordinary_empty"
                if font_ready != str(free).lower() or font_free != str(free).lower() or return_seen != "true" or return_free != "false":
                    raise AssertionError("Client readiness/return-seal mismatch: " + match.group(0))
                if "empty" in name and font_charge != 0:
                    raise AssertionError("Empty reservoir was refilled: " + match.group(0))
                if name == "font_half" and not 0 < font_charge < font_max:
                    raise AssertionError("Partial reservoir mismatch")
                if name == "font_full" and font_charge != font_max:
                    raise AssertionError("Full reservoir mismatch")
            if (actual != event or active != str(event != "NONE").lower() or current_dimension != dimension
                    or int(occurrence) != int(server_occurrence)):
                raise AssertionError("Client snapshot/dimension mismatch: " + match.group(0))
            expected_resource = ("better_celestial_events:textures/environment/" + event.lower() + "_phases.png"
                                 if event != "NONE" and dimension == "minecraft:overworld"
                                 else "minecraft:textures/environment/moon_phases.png")
            if resource != expected_resource:
                raise AssertionError("Wrong selected moon resource: " + resource)
            if resource.startswith("better_celestial_events:"):
                with zipfile.ZipFile(args.provider) as provider:
                    original = provider.read("assets/better_celestial_events/" + resource.split(":", 1)[1])
                if hashlib.sha256(original).hexdigest() != resource_hash:
                    raise AssertionError("Client moon bytes differ from bound provider")
            image = directory / "screenshots" / (filename + ".png")
            wait_for(log, r"FONT_VISUAL captured .*" + re.escape(filename) + r"\.png", 30, offset)
            if not image.is_file() or image.stat().st_size < 10000:
                raise AssertionError("Missing/empty native framebuffer: " + str(image))
            record = {"scene": name, "player": player, "event": actual, "active": active == "true",
                      "occurrence": int(occurrence), "remaining_ticks": int(remaining), "dimension": current_dimension,
                      "server_occurrence": int(server_occurrence), "server_remaining_ticks": int(server_remaining),
                      "moon_resource": resource, "moon_resource_sha256": resource_hash,
                      "font_charge": font_charge, "font_max": font_max,
                      "font_ready": font_ready == "true", "font_free": font_free == "true",
                      "return_seen": return_seen == "true", "return_free": return_free == "true",
                      "path": str(image), "sha256": digest(image)}
            captures.append(record)
            observed.append(record)
        if observed[0]["occurrence"] != observed[1]["occurrence"]:
            raise AssertionError("Two real clients disagree on event occurrence")

    def abort(signum, _frame):
        raise TimeoutError("Controller deadline/signal " + str(signum))

    for sig in (signal.SIGTERM, signal.SIGINT, signal.SIGALRM):
        signal.signal(sig, abort)
    signal.alarm(2400)
    try:
        (root / "tmp").mkdir()
        summary["font_artifact"] = launch["font_artifact"]
        if digest(Path(summary["font_artifact"]["path"])) != summary["font_artifact"]["sha256"]:
            raise AssertionError("Font candidate changed since source-runtime preparation")
        server_command = launch["server"]["command"]
        classpath = server_command[server_command.index("-cp") + 1].split(os.pathsep)
        summary["providers"] = {}
        for name, canonical in launch["providers"].items():
            raw = Path(canonical["path"])
            if digest(raw) != canonical["sha256"]:
                raise AssertionError("Canonical provider changed since launch preparation: " + name)
            mapped = [Path(value) for value in classpath if re.fullmatch(re.escape(name) + r"-.*_mapped_.*\.jar", Path(value).name)]
            if len(mapped) != 1:
                raise AssertionError("Expected one explicitly remapped provider: " + name)
            mapping_inputs = dict(line.split("=", 1) for line in Path(str(mapped[0]) + ".input").read_text().splitlines() if "=" in line)
            if mapping_inputs.get("orig") != hashlib.sha1(raw.read_bytes()).hexdigest():
                raise AssertionError("Forge mapped provider does not bind current canonical bytes: " + name)
            summary["providers"][name] = {**canonical, "mapped": str(mapped[0]), "mapped_sha256": digest(mapped[0])}
        if Path(launch["providers"]["better-celestial-events"]["path"]).resolve() != args.provider.resolve():
            raise AssertionError("Requested celestial provider differs from prepared source")
        summary["native_libraries"] = {str((args.native_library_dir / name).resolve()): digest(args.native_library_dir / name)
            for name in ("libflite.so", "libflite_cmu_us_kal16.so")}
        summary["audio_playback_acceptance"] = False
        server_dir = root / "server"
        server_dir.mkdir()
        prepare_definition(server_dir)
        (server_dir / "eula.txt").write_text("eula=true\n")
        flat = json.dumps({"biome": "minecraft:plains", "layers": [
            {"block": "minecraft:bedrock", "height": 1}, {"block": "minecraft:dirt", "height": 2},
            {"block": "minecraft:grass_block", "height": 1}], "features": False, "lakes": False, "structure_overrides": []})
        (server_dir / "server.properties").write_text("online-mode=false\nserver-ip=127.0.0.1\nserver-port=25566\nview-distance=5\nsimulation-distance=5\nspawn-protection=0\nmax-tick-time=120000\nlevel-type=minecraft:flat\nlevel-seed=73120261017\ngenerate-structures=false\ngenerator-settings=" + flat + "\n")
        server, server_log = start("server", java_command(launch["server"], "5G"), server_dir,
                                   launch["server"]["environment"], stdin=True)
        wait_for(server_log, r"Done \(", 600)
        command("gamerule doDaylightCycle false")
        command("gamerule doWeatherCycle false")
        command("weather clear")
        command("gamerule doMobSpawning false")
        command("difficulty peaceful")
        for slot, player in enumerate(("FontA", "FontB")):
            directory = root / player.lower()
            directory.mkdir()
            prepare_definition(directory)
            (directory / "alsoft.conf").write_text("[general]\nrt-prio=0\n")
            (directory / "options.txt").write_text("renderDistance:6\nsimulationDistance:5\nmaxFps:25\nenableVsync:false\npauseOnLostFocus:false\nparticles:0\ngamma:0.5\nentityDistanceScaling:0.5\nfov:0.0\n")
            display = ":" + str(200 + os.getpid() % 300 + slot)
            xvfb, _ = start("xvfb-" + player, ["Xvfb", display, "-screen", "0", "1600x900x24", "-nolisten", "tcp"], directory)
            time.sleep(1)
            if xvfb.poll() is not None:
                raise RuntimeError("Xvfb failed for " + player)
            cmd = java_command(launch["client"], "3G")
            set_arg(cmd, "--username", player)
            set_arg(cmd, "--gameDir", str(directory))
            env = dict(launch["client"]["environment"])
            env.update({"DISPLAY": display, "LIBGL_ALWAYS_SOFTWARE": "1", "MALLOC_ARENA_MAX": "2", "ALSOFT_DRIVERS": "null",
                        "LD_LIBRARY_PATH": str(args.native_library_dir.resolve()), "ALSOFT_CONF": str(directory / "alsoft.conf")})
            _, log = start("client-" + player, cmd, directory, env)
            wait_for(server_log, re.escape(player) + r" joined the game", 600)
            wait_for(log, r"Loaded [0-9]+ advancements", 90)
            clients.append((player, directory, log))
            command("gamemode spectator " + player)
        command("fontvisual prepare FontA nether forest")
        command("fontvisual view FontB close")
        for event in ("thin_veil", "spirit_fair", "wandering_lights", "open_ways"):
            command("celestial force " + event)
            command("time set 18000")
            for player, _, _ in clients:
                command("fontvisual view " + player + " sky")
            capture("moon_" + event, event.upper())
        command("celestial end")
        capture("moon_ordinary_restored", "NONE")
        for player, _, _ in clients:
            # Vanilla nearest-viewer lookup excludes spectators. Creative observers are real
            # nearby viewers for particle scenes; actual free entry below remains Survival.
            command("gamemode creative " + player)
            command("fontvisual view " + player + " close")
        command("fontvisual charge FontA empty")
        capture("font_ordinary_empty", "NONE")
        command("celestial force open_ways")
        command("time set 18000")
        for player, _, _ in clients:
            command("fontvisual view " + player + " close")
        for state in ("empty", "half", "full"):
            command("fontvisual charge FontA " + state)
            capture("font_" + state, "OPEN_WAYS")
        for player, _, _ in clients:
            command("fontvisual view " + player + " control")
        capture("font_return_control", "OPEN_WAYS")
        command("fontvisual charge FontA empty")
        for player, _, _ in clients:
            command("gamemode survival " + player)
            offset = len(text(server_log))
            command("fontvisual enter " + player)
            wait_for(server_log, r"FONT_VISUAL entered=true player=" + player + r" dimension=minecraft:the_nether", 90, offset)
        capture("travel_free_before_dawn", "OPEN_WAYS", "minecraft:the_nether")
        command("time set 24000")
        # Normal per-second Font drain must perform real safe extraction, not a harness teleport.
        time.sleep(3)
        capture("travel_dawn_extracted", "NONE")
        summary["status"] = "passed_observations_visual_review_pending"
    except BaseException as failure:
        summary["status"] = "failed"
        summary["error"] = repr(failure)
        raise
    finally:
        signal.alarm(0)
        # Ask native clients to stop normally, keeping Xvfb alive until GL/audio close.
        # Exit 1 from prematurely removing their X connection is never accepted.
        if summary["status"] == "passed_observations_visual_review_pending":
            try:
                for player, _, _ in clients:
                    command("fontvisual quit " + player)
                for label, process, _ in processes:
                    if label.startswith("client-") and process.wait(timeout=45) != 0:
                        raise RuntimeError(label + " did not exit cleanly: " + str(process.returncode))
            except (OSError, subprocess.TimeoutExpired, RuntimeError) as failure:
                summary["status"] = "failed"
                summary["error"] = "Native client shutdown failed: " + repr(failure)
        # No producer or fixture survives either outcome; evidence remains only for active review.
        if server is not None and server.poll() is None:
            try:
                server.stdin.write("stop\n")
                server.stdin.flush()
            except (BrokenPipeError, OSError):
                pass
        for prefix in ("client-", "xvfb-"):
            for label, process, _ in reversed(processes):
                if label.startswith(prefix) and process.poll() is None:
                    os.killpg(process.pid, signal.SIGTERM)
                    try:
                        process.wait(timeout=20)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait(timeout=15)
        deadline = time.monotonic() + 90
        for _, process, _ in processes:
            try:
                process.wait(timeout=max(0.1, deadline - time.monotonic()))
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=15)
        if (summary["status"] == "passed_observations_visual_review_pending"
                and (server is None or server.returncode != 0)):
            summary["status"] = "failed"
            summary["error"] = "Native server did not exit cleanly"
        if "font_artifact" in summary and digest(Path(summary["font_artifact"]["path"])) != summary["font_artifact"]["sha256"]:
            summary["status"] = "failed"
            summary["error"] = "Font candidate changed during the source-runtime fixture"
        if digest(args.provider) != summary["provider_sha256"] or any(
                digest(item["path"]) != item["sha256"] or digest(item["mapped"]) != item["mapped_sha256"]
                for item in summary.get("providers", {}).values()):
            summary["status"] = "failed"
            summary["error"] = "Provider bytes changed during the focused fixture"
        summary["process_cleanup"] = {"complete": all(p.poll() is not None for _, p, _ in processes),
                                      "processes": [{"label": label, "pid": p.pid, "exit": p.returncode} for label, p, _ in processes]}
        for handle in handles:
            handle.close()
        # Deliberately report all warnings/errors for semantic classification, never a blanket whitelist.
        for label, _, log in processes:
            for number, line in enumerate(text(log).splitlines(), 1):
                if re.search(r"\b(WARN|ERROR|FATAL)\b", line):
                    findings.append({"process": label, "line": number, "message": line})
        if findings and summary["status"] == "passed_observations_visual_review_pending":
            summary["status"] = "observations_complete_diagnostics_review_pending"
        (root / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
        print(json.dumps({"status": summary["status"], "summary": str(root / "summary.json")}))
    if summary["status"] == "failed":
        raise AssertionError(summary.get("error", "Focused fixture failed"))


if __name__ == "__main__":
    main()
