#!/usr/bin/env python3
"""Small, deterministic Health Connect UI smoke test using Android's accessibility tree.

Does not request permission, read exercise records, or modify habit data.
Runs only after the standard HabitFlow emulator/database/accessibility smoke suite.
"""

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.nitinjadhav.habitflow.debug"
OUT = Path("emulator-evidence")
DUMP_PATH = "/sdcard/habitflow-healthconnect-window.xml"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(*args, timeout=40):
    return subprocess.run(
        ["adb", *args], capture_output=True, check=True, timeout=timeout
    ).stdout


def nodes():
    adb("shell", "uiautomator", "dump", DUMP_PATH, timeout=40)
    raw = adb("exec-out", "cat", DUMP_PATH)
    tree = ET.fromstring(raw)
    (OUT / "health-connect-window.xml").write_bytes(raw)
    return [item.attrib for item in tree.iter("node")]


def match(item, label, exact):
    for field in ("text", "content-desc"):
        value = item.get(field, "")
        if (value == label if exact else label in value):
            return True
    return False


def locate(label, exact=True, attempts=12):
    for attempt in range(attempts):
        try:
            found = next((node for node in nodes() if match(node, label, exact)), None)
        except (subprocess.SubprocessError, ET.ParseError) as error:
            print(f"Accessibility tree retry {attempt + 1}: {error}", flush=True)
            found = None
        if found is not None:
            return found
        time.sleep(1)
    raise RuntimeError(f"UI label not found after {attempts} attempts: {label}")


def locate_scrolling(label, exact=False, max_swipes=5):
    """Find real visible UI text; scroll rather than silently bypassing it."""
    for swipe in range(max_swipes + 1):
        current = nodes()
        found = next((node for node in current if match(node, label, exact)), None)
        if found is not None:
            return found
        if swipe < max_swipes:
            adb("shell", "input", "swipe", "520", "1640", "520", "540", "370")
            time.sleep(1)
    raise RuntimeError(f"UI label not found after scrolling: {label}")


def tap(label, exact=True):
    item = locate(label, exact=exact)
    bounds = BOUNDS.fullmatch(item.get("bounds", ""))
    if not bounds:
        raise RuntimeError(f"No bounds for {label}: {item}")
    left, top, right, bottom = map(int, bounds.groups())
    x, y = (left + right) // 2, (top + bottom) // 2
    adb("shell", "input", "tap", str(x), str(y))
    print(f"Tapped {label} at ({x}, {y})", flush=True)


def screenshot(path):
    img = adb("exec-out", "screencap", "-p", timeout=25)
    if not img.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError(f"Invalid PNG screenshot: {path}")
    (OUT / path).write_bytes(img)


def assert_no_unprompted_health_permission():
    """An app launch must not surface the Health Connect grant dialog."""
    visible = nodes()
    unexpected = (
        "Allow HabitFlow to access",
        "Choose data to share with HabitFlow",
        "Grant read-only access",
    )
    for phrase in unexpected:
        if any(match(item, phrase, exact=False) for item in visible):
            raise RuntimeError(f"Unexpected Health Connect permission UI on launch: {phrase}")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "monkey", "-p", PACKAGE, "-c",
        "android.intent.category.LAUNCHER", "1")
    locate("More")
    assert_no_unprompted_health_permission()
    tap("More")
    tap("Settings")
    tap("Health Connect")
    locate("Optionally allow HabitFlow", exact=False)
    # A fresh install with no mappings must not offer a grant action. The
    # missing-provider state must remain usable without granting access.
    visible = nodes()
    if any(match(item, "Save a habit mapping below", exact=False) for item in visible):
        if any(match(item, "Grant read-only access", exact=True) for item in visible):
            raise RuntimeError("Health permission grant offered before mapping opt-in")
    if any(match(item, "Health Connect is unavailable", exact=False) for item in visible):
        locate("continue tracking habits manually", exact=False)
    locate_scrolling("HabitFlow requests only exercise-session read access.", exact=False)
    locate_scrolling("Exercise completion history", exact=True)
    screenshot("health-connect-settings.png")
    print("Health Connect Settings and privacy explanation visible", flush=True)

    # Android may launch this Activity from the Health Connect permission UI.
    adb("shell", "am", "start", "-W", "-n",
        f"{PACKAGE}/com.ofalvai.habittracker.ui.HealthConnectRationaleActivity")
    locate("Why HabitFlow requests access")
    locate("HabitFlow can read exercise sessions", exact=False)
    screenshot("health-connect-rationale.png")
    print("Health Connect permissions rationale visible; no health data accessed", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Health Connect UI smoke FAILED: {error}", file=sys.stderr, flush=True)
        sys.exit(1)
