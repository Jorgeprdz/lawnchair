#!/usr/bin/env python3
"""Capture emulator frames requested by instrumentation, using ADB only."""
import pathlib
import re
import signal
import struct
import subprocess
import time

DEST = pathlib.Path("oneui-screenshots")
REMOTE = "/storage/emulated/0/Download/lawnchair"
DEST.mkdir(exist_ok=True)
subprocess.run(["adb", "shell", "mkdir", "-p", REMOTE], check=True, timeout=15)
def stop(*_):
    raise SystemExit(0)

signal.signal(signal.SIGTERM, stop)
signal.signal(signal.SIGINT, stop)
completed = set()
while True:
    listing = subprocess.run(["adb", "shell", "ls", REMOTE], check=True,
                             timeout=15, capture_output=True, text=True)
    for line in listing.stdout.splitlines():
        match = re.fullmatch(r"([0-9]{2}-[a-z0-9-]+\.png)\.request", line.strip())
        if not match or match.group(1) in completed:
            continue
        name = match.group(1)
        subprocess.run(["adb", "shell", "screencap", "-p", f"{REMOTE}/{name}"], check=True, timeout=20)
        output = DEST / name
        subprocess.run(["adb", "pull", f"{REMOTE}/{name}", str(output)], check=True, timeout=20)
        data = output.read_bytes()
        if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
            raise RuntimeError(f"Invalid PNG: {name}")
        width, height = struct.unpack(">II", data[16:24])
        if not width or not height:
            raise RuntimeError(f"Empty PNG: {name}")
        subprocess.run(["adb", "shell", "touch", f"{REMOTE}/{name}.done"], check=True, timeout=15)
        print(f"Captured {name}: {width}x{height}", flush=True)
        completed.add(name)
    time.sleep(0.2)
