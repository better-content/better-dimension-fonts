#!/usr/bin/env python3
"""Serialize the entire command, including native runtime, with all workspace Forge users."""
import fcntl
import os
from pathlib import Path
import subprocess
import sys

lock = Path.home() / ".worklane" / "forge-cache.lock"
lock.parent.mkdir(parents=True, exist_ok=True)
with lock.open("a+") as handle:
    fcntl.lockf(handle, fcntl.LOCK_EX)
    temporary = Path.cwd() / ".gradle" / "native-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    os.environ.update({"TMPDIR": str(temporary), "TMP": str(temporary), "TEMP": str(temporary)})
    os.environ["JAVA_TOOL_OPTIONS"] = os.environ.get("JAVA_TOOL_OPTIONS", "") + " -Djava.io.tmpdir=" + str(temporary)
    raise SystemExit(subprocess.call(sys.argv[1:], pass_fds=(handle.fileno(),)))
