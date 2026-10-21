# Better Dimension Fonts

## Playtest logging

`bc.playtest.better_dimension_fonts.lifecycle` observes preparation/activation/entry rejection,
charged-and-bound entry and close cleanup. `.transport` distinguishes actual backend return
from rejected/not-bound returns, including whether the existing run binding remains retryable.
Existing run/origin UUIDs correlate preparation, entry, return and close, including rejection.
Entry/return also carry the native participant UUID and current dimension/game tick. Callbacks
holding only cached operation IDs do not query a world solely for context; unavailable IDs are
honestly `UNKNOWN`. No traveler names, inventory or charge-tick stream is logged. `.rewards` reports
the native currency-ejection schedule only after queue insertion, with burst count and causal source
position/dimension/game tick. These bounded native UUIDs/positions are intentional, not an entity or
per-block stream; no inventory contents or extra diagnostic registry/list probes are introduced. Duplicate legacy
backend activation/close INFO records are removed; exact native fixture/parser witnesses remain
unchanged. Close cleanup does not claim successful transport for every traveler. DEBUG/TRACE share
120/minute (burst 20), 256 keys and 2,048-character records, with 30-second summaries. Startup INFO
reports version/mode/limits and Celestial presence; unavailable source identity is `UNKNOWN`.
Stop/disconnect clears transient diagnostics without rearming process allowances. Pure domain
emitter/contracts supplement, not replace, absent-provider and native transport gates.

## Scope and authority

This repository owns its mod-specific behavior and authoring inputs. Read [local instructions](AGENTS.md)
and the [shared documentation/policy index](../../better-content-modpack/docs/README.md).


Pack-owned obelisk and charge-font worldgen/runtime mod for Forge `1.20.1`.

## Common commands

```bash
./gradlew verifyFast
./gradlew verifyFull
./gradlew verifySmoke
```

`verifyFast` runs the JVM verification lane. `verifyFull` adds **all production
GameTests** without the optional celestial provider. `verifySmoke` adds only the lifecycle smoke
tests. `./gradlew headlessGameTest -PdimensionDrinkGameTestSelection=runtime` selects a focused
profile; supported selectors are `all`, `smoke`, `run`, `activation`, `rewards`,
`void`, `data`, `template`, `multiplayer`, `commands`, `runtime`, and the explicit provider-present
`celestial` supplement. Unknown selectors
and overrides conflicting with `verifyFull` or `verifySmoke` fail.

If an incremental Java compilation leaves the Mixin annotation processor's
`compileJava-mappings.tsrg` absent, regenerate it through its real producer before
staging: `./gradlew compileJava --rerun verifyFull stageRuntimeJar --no-daemon --offline`.
The task-specific `--rerun` forces the annotated Java sources to compile; it does not
fabricate mappings or bypass verification.

During the active task, each GameTest invocation keeps its generated world, logs, and `execution.json` under
`build/gametest/<run-id>/`. The gate compares actual runtime registration and
successful completion against the reviewed IDs in `gametest/profiles/`, rejects
missing or incomplete evidence, and records the selected profile and run ID.
Inspect failed evidence during diagnosis. A later run uses a new fixture; retain previous
worlds/reports and caches/build outputs across handoffs by default under the shared
[retention policy](../../better-content-modpack/docs/policies/generated-data.md). Finishing activity deletes nothing. Runtime charge scenarios
use deterministic modifier fixtures and separate batches; worldgen scenarios
prepare their terrain and worldgen heightmaps explicitly. `verifyFast` also runs
12 negative execution-evidence checks without starting Forge.

## Font generation and discovery

Better Dimension Fonts bundles the Aether, Bumblezone, and Nether expedition Font definitions at equal default `worldgenWeight`. A weight controls deterministic selection at an eligible structure start; it does not guarantee equal visible counts in a finite explored area. Terrain rejection, exploration history, destroyed Fonts, and map sales are measured separately from configured probability.

Natural structure placement retains one third of candidate starts (`frequency: 0.33333334`), keeping the existing 30-chunk spacing and 9-chunk separation. This reduces expected wild Font density to one third without changing destination weights. It affects newly generated terrain only; existing Fonts are not removed.

Layout and definition selection use independent deterministic seed domains, so terrain opportunity cannot systematically favor a Font type. Custom JSON definitions remain supported when their weights are positive and finite. The effective normalized weights are logged on reload and available with the permission-level-2 `/font audit` command.

Use the permission-level-2 `/font find` command to list one indexed natural Font for each enabled worldgen definition. Found coordinates are clickable and teleport the operator to the site. The command only reads locations recorded as generated chunks load; it does not search or generate remote terrain. A missing type means more terrain must be explored before that world's generated Fonts can be fully checked.

Naturally generated Fonts are added to a saved discovery index as their chunks load. Wandering-trader Font maps use only that index and never locate or generate remote structure chunks; maps are marker-only until players explore their terrain. Harvesting a bound Font drops an unbound Font: it can be replanted as a World Condenser base but cannot start another expedition. Harvesting during an active run is blocked.

New Overworld Font sites use a circular oxidized-copper court without water pools, candles, and biome-matched stripped-log supports only where trees grow. Bumblezone sites include dense wax and hive clusters. Font maps display their destination's salience aspects; themed spirit sellers offer only matching surveyed destinations. Underwater return Fonts preserve the water and use four soul-sand corners as bubble shafts to the surface.

New Aether arrival sites must retain at least 1,024 existing `aether`-namespace blocks in the nine chunks centered on the landing chunk after the return court's projected floor, support-column, clearance, and return-Font writes. Sparse candidates are skipped within the existing configured search radius, before any court is placed. If none qualifies, entry is rejected without spending the opening entry charge or binding the player; no emergency Aether island is built and unarrived-site rollback does not clear natural mobs. Preparation/activation/rollback still use the normal site lifecycle. Saved arrival positions are reused unchanged, and other dimensions keep their existing arrival behavior.

Vanilla plains, desert, savanna, snowy, and taiga villages can include a Font shrine. The shrine joins their house pools with a street-facing entrance. Its selection rate targets roughly one shrine in five newly generated villages when a village makes twelve house placement attempts; actual rates vary with village layout and available space.

The craftable Font Pourer sits two blocks above a Font, leaving one air block for a visible pour. It accepts the matching Nether, Aether, or Bumblezone Libation by bucket or fluid automation and restores charge only while a run is active. One bucket supplies 48,000 charge, about ten extra minutes at the current 80 charge per second drain; a nominal 15,000-charge Font lasts roughly three minutes before entry costs. The Font itself has no fluid capability.

Site-layout changes are new-world-only. Historical structures and saves are not migrated or scanned. Ratlantis is retired: the loader ignores retained definitions with the `ratlantis` ID or a `ratlantis` / `rats:ratlantis` destination (including legacy template IDs), even while Rats remains installed. Their files are preserved, but they cannot generate new sites or map offers. Already-generated Ratlantis structures are not removed from saved worlds.

`FontAggregateReturnEvent` posts once for every living participant actually transported back to
the origin, including a voluntary return, charge-expiry extraction, and a Font-bound Aether
fall-out. It is factual extraction evidence rather than a challenge reward. Final death, logout
without transport, rejected transport, and server-stop cleanup do not emit it. Aether departure
handling applies only while the player is bound to an Aether Font session. When Better Content
Threads is present, the optional bridge reuses the active `the_end_is_not_a_door` correlation token
to emit `font_route_completed=returned`.

## Open Ways (optional Better Celestial Events integration)

When `better_celestial_events` is installed, its typed snapshot controls Open Ways using the
Overworld night clock. Existing and new valid bound Fonts waive opening, joining, and ongoing
charge drain until dawn, including an empty Font. This does not bypass destination validation,
session admission, cooldown, origin-ticket or safety limits. No charge is refilled and no entry
cost is charged retroactively. Stored charge and fractional drain remain intact; ordinary idle
recharge is unchanged. Pourers pause fluid consumption, charge restoration, and their partial
pour cycle. At dawn the usual drain resumes, including normal safe extraction from a dry Font.
Travellers see a free-until-dawn bar and warnings at 1,000 and 200 remaining Overworld ticks.

Client-only verdigris/gold court streams, central plumes, and pulses are bounded to 42 motes per
second per nearby valid Font (32-block viewing radius), even with an empty reservoir. They never
fill the reservoir visually and do not decorate return seals. The optional bridge registers a
read-only natural-Font locator with the provider; it uses only the discovery index and already
loaded chunk checks, retaining indexed unloaded locations as unknown rather than generating terrain.
With the provider absent, the bridge is inert. There is no reflection or reverse Font dependency.

Prepare the canonical `better-celestial-events-0.1.0.jar` before compiling, either in the sibling
`build/libs` directory or through `BC_CUSTOM_MOD_JAR_DIR` (which also supplies other local providers).
The runtime remains compile-only by default. In addition to the full absent-provider gate, run the
explicit native supplement with a freshly prepared provider:

```bash
python3 scripts/forge-cache-lock.py ./gradlew headlessGameTest \
  -PdimensionDrinkGameTestSelection=celestial -PdimensionDrinkIncludeCelestialRuntime=true --no-daemon
```

The non-shipping celestial real-client profile uses only mandatory Font providers plus Sleeping
Overhaul 2 and Architectury. Its Nether route does not load unrelated optional dimension mods. Prepare launches with `prepareCelestialVisualHarness` and
`-PdimensionDrinkIncludeCelestialRuntime=true`, then run
`python3 scripts/celestial-visual-harness.py --launch build/visual/launch.json --output ABSENT_OUTPUT_DIRECTORY --provider /absolute/path/to/better-celestial-events-0.1.0.jar --native-library-dir /absolute/path/to/inventoried/flite/lib`.
Use the user-local native SDK recorded in [toolchains](../../better-content-modpack/docs/toolchains.md).
The fixture supplies its explicit loader path and uses a null OpenAL backend without real-time
priority requests; audio playback is not an acceptance claim. Its declared non-shipping Nether
definition sets passive regeneration to zero solely to hold empty/partial/full states steady across
both client frames; production regeneration remains unchanged. Local provider launches verify
canonical-to-mapped Forge input hashes. Stage the passed Font source candidate before preparing
visual launches. Font itself runs from compiled source `MOD_CLASSES`, not its mapped production JAR;
the export separately records and checks the corresponding staged Font checksum without claiming
that JAR is loaded by this source fixture.
Hold one shared Forge-cache lock across preparation and the entire controller run. The controller
starts one fresh server and two real software-rendered clients, captures four special moons and
ordinary-moon restoration, empty/partial/full Font particle scenes, and real Font entry/dawn
extraction. It uses native commands/framebuffer capture, not synthetic pointer input. Its summary
reports observations, all diagnostic records, and owned-process cleanup; diagnostic classification
and actual image inspection remain required before claiming acceptance. This is a focused mod
fixture, not full-pack or production-JAR acceptance. Clients must close Minecraft normally before
Xvfb is stopped; a reaped process is not evidence of a successful exit.

`verifyFast` includes diagnostic-classifier regression tests. Every native GameTest invocation
checks both `latest.log` and `debug.log` against current-invocation metadata exported by the
non-shipping native fixture, exact Forge/Minecraft/language/mixin resource inputs, and actual
registered config defaults. WARN presence, wording, repetition, missing classification, or timing
alone does not fail correctness. Compatibility classifications remain input/context-bound diagnostic
observations, not warning-count allowances. Actual failed operations (including script/registry,
assertion, corruption and crash signals) fail regardless of severity; every ERROR/FATAL blocks even
when its message resembles familiar advisory text. Input hashes, fixture/version identity, native
execution completeness, deadlines and process cleanup remain independent gates. This parser does
not invent a performance budget from a timing notice. The generated refmap remains required on
native bootstrap classpaths. For real clients, run
`python3 scripts/check_native_diagnostics.py /absolute/path/to/each/native/process/directory`
after shutdown, separately for the server and both clients. Actual image inspection remains separate.
Native GameTests use an explicitly authored flat Overworld without unrelated vanilla structures;
generation tests create their deterministic terrain surfaces and exercise the real Font site generator.
The generation test schedules bounded chunk loading and its twelve surface/generation/search steps
across native ticks, preserving the same sampled inputs and altar assertions rather than suppressing
timing diagnostics or blocking one tick on all terrain setup and block scans.
The fixture initializes Netty's IPv4 preference for its declared loopback-only network, not production
network settings. Native/visual fixture classes and diagnostic metadata are forbidden in the staged JAR.

## Release artifact

Use the staged reobfuscated runtime jar for pack deployment:

- `build/libs/better-dimension-fonts-<version>.jar`

`stageRuntimeJar` copies `build/reobfJar/output.jar` onto that canonical release path so pack deployment does not need a repo-specific rename rule.

## Community and support

For modpack and mod discussion, playtest feedback, and bug reports, join the [Better Content Discord](https://discord.gg/EkRnZbzqS9).

## Canonical identity

- Repository and Gradle project: `better-dimension-fonts`
- Mod ID and resource namespace: `better_dimension_fonts`
- Maven group: `com.bettercontent`
- Runtime artifact: `build/libs/better-dimension-fonts-<version>.jar`

The canonical identity is a clean break. Legacy mod IDs, resource namespaces, configuration paths, commands, network channels, and saved-data keys are not migrated or aliased.
