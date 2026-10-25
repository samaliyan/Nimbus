#!/usr/bin/env python3
"""Drives the app on an emulator through adb for the end-to-end check in CI.

Finds controls by their visible text in a uiautomator dump, so the script works in both
languages and both layout directions without screen coordinates.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.samaliyan.nimbus"
SHOTS = os.environ.get("SHOTS", "shots")


def adb(*args, check=True, capture=True):
    r = subprocess.run(["adb", *args], capture_output=capture, text=True, timeout=120)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr}")
    return r.stdout


def shell(cmd, check=True):
    return adb("shell", cmd, check=check)


def dump():
    for _ in range(5):
        out = shell("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml", check=False)
        if "<hierarchy" in out:
            return ET.fromstring(out[out.index("<?xml") if "<?xml" in out else out.index("<hierarchy"):])
        time.sleep(1)
    raise RuntimeError("uiautomator dump failed")


def find(text, exact=False):
    for node in dump().iter("node"):
        values = [node.get("text") or "", node.get("content-desc") or ""]
        for v in values:
            if (v == text) if exact else (text in v):
                m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds"))
                x1, y1, x2, y2 = map(int, m.groups())
                return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def screen_size():
    out = shell("wm size")
    w, h = re.findall(r"(\d+)x(\d+)", out)[-1]
    return int(w), int(h)


def tap(text, exact=False, timeout=20, scroll=False):
    """Taps the control showing text; with scroll, swipes the page up until it appears."""
    w, h = screen_size()
    end = time.time() + timeout
    swipes = 0
    while time.time() < end:
        pos = find(text, exact)
        if pos and (not scroll or 0 < pos[1] < h * 0.88):
            shell(f"input tap {pos[0]} {pos[1]}")
            time.sleep(1.2)
            return
        if scroll and swipes < 10:
            shell(f"input swipe {w // 2} {int(h * 0.75)} {w // 2} {int(h * 0.35)} 300")
            swipes += 1
        time.sleep(1)
    shot(f"missing-{re.sub(r'[^a-zA-Z0-9]+', '_', text)[:30]}")
    raise RuntimeError(f"no control with text {text!r} on screen")


def wait_text(texts, timeout):
    """Waits until any of texts is on screen; returns the one found."""
    if isinstance(texts, str):
        texts = [texts]
    end = time.time() + timeout
    while time.time() < end:
        xml = ET.tostring(dump(), encoding="unicode")
        for t in texts:
            if t in xml:
                return t
        time.sleep(2)
    return None


def to_top():
    w, h = screen_size()
    for _ in range(4):
        shell(f"input swipe {w // 2} {int(h * 0.3)} {w // 2} {int(h * 0.85)} 200")
    time.sleep(0.8)


def page_down():
    w, h = screen_size()
    shell(f"input swipe {w // 2} {int(h * 0.75)} {w // 2} {int(h * 0.3)} 300")
    time.sleep(0.8)


def shot(name):
    os.makedirs(SHOTS, exist_ok=True)
    path = os.path.join(SHOTS, f"{name}.png")
    with open(path, "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True, timeout=60)
    print(f"screenshot {path}")


if __name__ == "__main__":
    cmd, *rest = sys.argv[1:]
    if cmd == "top":
        w, h = screen_size()
        for _ in range(4):
            shell(f"input swipe {w // 2} {int(h * 0.3)} {w // 2} {int(h * 0.85)} 200")
    elif cmd == "tap":
        tap(rest[0])
    elif cmd == "shot":
        shot(rest[0])
    elif cmd == "wait":
        found = wait_text(rest[1:], int(rest[0]))
        print(found or "")
        sys.exit(0 if found else 1)
    else:
        raise SystemExit(f"unknown command {cmd}")
