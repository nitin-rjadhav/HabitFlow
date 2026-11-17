#!/usr/bin/env python3
"""Best-effort real-emulator dashboard evidence, using Android accessibility hierarchy."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path("emulator-evidence")
OUT.mkdir(exist_ok=True)
PKG = "com.nitinjadhav.habitflow.debug"
report = []

def adb(*args, timeout=30):
    return subprocess.run(["adb", *args], check=True, capture_output=True, timeout=timeout).stdout

def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml", timeout=40)
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/window.xml"))

def nodes():
    return list(hierarchy().iter("node"))

def center(node):
    b = node.attrib.get("bounds", "")
    values = list(map(int, re.findall(r"\\d+", b)))
    if len(values) != 4:
        raise RuntimeError("No bounds: " + str(node.attrib))
    return ((values[0] + values[2]) // 2, (values[1] + values[3]) // 2)

def tap(node):
    x, y = center(node)
    adb("shell", "input", "tap", str(x), str(y))

def find(text):
    matches = [n for n in nodes() if text.lower() in (n.attrib.get("text", "") + " " + n.attrib.get("content-desc", "")).lower()]
    if not matches:
        raise RuntimeError("Cannot find UI label: " + text)
    return matches[0]

def screenshot(name):
    data = adb("exec-out", "screencap", "-p", timeout=30)
    if not data.startswith(b"\\x89PNG"):
        raise RuntimeError("Invalid screenshot")
    (OUT / name).write_bytes(data)

def assert_count(expected):
    for _ in range(8):
        if any(expected in (n.attrib.get("text", "") + " " + n.attrib.get("content-desc", "")) for n in nodes()):
            report.append("PASS: " + expected)
            return
        time.sleep(1)
    raise RuntimeError("Expected progress count not shown: " + expected)

try:
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(5)
    screenshot("progress-empty.png")
    # Start from the empty dashboard; preserve existing smoke evidence independently.
    tap(find("Create your first habit"))
    time.sleep(2)
    # Name field receives focus on entering the form.
    adb("shell", "input", "text", "Meditation")
    tap(find("Save"))
    time.sleep(4)
    assert_count("0 of 1 completed")
    screenshot("progress-zero-of-one.png")
    # Android exposes each daily action with a relative date and toggle state.
    # Find today's circle via accessible text; long press is required by this app.
    today = [n for n in nodes() if "today" in (n.attrib.get("text", "") + " " + n.attrib.get("content-desc", "")).lower()]
    if not today:
        raise RuntimeError("Today's action circle not exposed in accessibility tree")
    x, y = center(today[-1])
    adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "1100")
    time.sleep(3)
    assert_count("1 of 1 completed")
    screenshot("progress-one-of-one.png")
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(4)
    assert_count("1 of 1 completed")
    screenshot("progress-relaunch.png")
except Exception as exc:
    report.append("INCOMPLETE: " + str(exc))
    try:
        screenshot("progress-diagnostic.png")
        (OUT / "progress-hierarchy.xml").write_bytes(adb("exec-out", "cat", "/sdcard/window.xml"))
    except Exception:
        pass
finally:
    (OUT / "progress-check.txt").write_text("\\n".join(report) + "\\n")
    print("\\n".join(report))
