#!/usr/bin/env python3
"""Real-emulator progress and accessibility-node evidence (not TalkBack activation)."""
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
    last_error = None
    for attempt in range(5):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/window.xml", timeout=40)
            xml = adb("exec-out", "cat", "/sdcard/window.xml")
            root = ET.fromstring(xml)
            if root.tag != "hierarchy":
                raise ValueError("Unexpected hierarchy root")
            return root
        except (ET.ParseError, ValueError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
            last_error = exc
            time.sleep(2)
    raise RuntimeError("UI hierarchy unavailable after 5 attempts: " + str(last_error))

def nodes():
    return list(hierarchy().iter("node"))

def center(node):
    b = node.attrib.get("bounds", "")
    values = list(map(int, re.findall(r"\d+", b)))
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

def dismiss_launcher_anr(current_nodes):
    """Dismiss a system launcher ANR if it obscures the app under test."""
    if not any("Pixel Launcher isn\'t responding" in n.attrib.get("text", "") for n in current_nodes):
        return False
    close = next((n for n in current_nodes if n.attrib.get("resource-id") == "android:id/aerr_close"), None)
    if close is None:
        return False
    tap(close)
    report.append("INFO: dismissed unresponsive Pixel Launcher dialog")
    return True

def screenshot(name):
    data = adb("exec-out", "screencap", "-p", timeout=30)
    if not data.startswith(b"\x89PNG"):
        raise RuntimeError("Invalid screenshot")
    (OUT / name).write_bytes(data)

def assert_count(expected):
    last_nodes = []
    # CI emulators can be slow to navigate back and refresh Room-backed totals.
    for _ in range(20):
        last_nodes = nodes()
        if any(expected in (n.attrib.get("text", "") + " " + n.attrib.get("content-desc", "")) for n in last_nodes):
            report.append("PASS: " + expected)
            return
        time.sleep(1)
    diagnostic = "\\n".join(str(n.attrib) for n in last_nodes)
    (OUT / "progress-count-timeout-hierarchy.txt").write_text(diagnostic)
    print("Progress assertion failed; current visible UI nodes:", flush=True)
    for node in last_nodes[:80]:
        print(node.attrib, flush=True)
    raise RuntimeError("Expected progress count not shown: " + expected)

try:
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(5)
    # Wait for the dashboard accessibility tree before capturing the launch frame.
    # A screenshot taken immediately after activity launch can be blank.
    initial_nodes = []
    # Emulator cold starts can display a splash/loading frame after monkey returns.
    # Poll the actual dashboard CTA rather than failing on a single early dump.
    for attempt in range(12):
        initial_nodes = nodes()
        if dismiss_launcher_anr(initial_nodes):
            time.sleep(2)
            initial_nodes = nodes()
        (OUT / "progress-launch-hierarchy.txt").write_text(
            "\n".join(str(n.attrib) for n in initial_nodes)
        )
        if any("create" in (n.attrib.get("text", "") + " " + n.attrib.get("content-desc", "")).lower()
               for n in initial_nodes):
            break
        time.sleep(3)
    else:
        print("Dashboard readiness diagnostics (last UI hierarchy):", flush=True)
        for node in initial_nodes[:60]:
            print(node.attrib, flush=True)
        raise RuntimeError("Dashboard not ready after 36 seconds; inspect progress-launch-hierarchy.txt")
    time.sleep(2)
    screenshot("progress-empty.png")
    # Record the actual launch screen even when onboarding or restored state differs.
    (OUT / "progress-launch-hierarchy.txt").write_text(
        "\n".join(str(n.attrib) for n in initial_nodes)
    )
    # The empty dashboard has a dedicated CTA; populated dashboards use
    # a separate 'Create habit' button. Avoid assuming a fresh app state.
    create = [
        n for n in initial_nodes
        if any(label in (n.attrib.get("text", "") + " " + n.attrib.get("content-desc", "")).lower()
               for label in ("create your first habit", "create habit"))
    ]
    if not create:
        raise RuntimeError("No create-habit control on launch; inspect progress-launch-hierarchy.txt")
    tap(create[0])
    time.sleep(2)
    (OUT / "progress-form-hierarchy.txt").write_text(
        "\n".join(str(n.attrib) for n in nodes())
    )
    # Focus the editable habit-name field rather than assuming initial focus.
    fields = [n for n in nodes() if n.attrib.get("class") == "android.widget.EditText"]
    if not fields:
        raise RuntimeError("Habit form has no accessible name field")
    tap(fields[0])
    adb("shell", "input", "text", "Meditation")
    # Dismiss the IME before tapping Save: it may intercept the first tap
    # while the focused text field remains active.
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)
    save = find("Create habit")
    tap(save)
    time.sleep(4)
    # A failed save must be diagnosed as a form-navigation problem, not
    # misreported as a dashboard progress-count failure.
    after_save = nodes()
    if any(n.attrib.get("text") == "Create new habit" for n in after_save):
        (OUT / "progress-save-still-on-form.txt").write_text(
            "\\n".join(str(n.attrib) for n in after_save)
        )
        raise RuntimeError("Create Habit form remained open after Save tap")
    assert_count("0 of 1 completed")
    screenshot("progress-zero-of-one.png")
    # Android exposes each daily action with a relative date and toggle state.
    # Find today's circle via accessible text; long press is required by this app.
    visible_nodes = nodes()
    (OUT / "progress-accessibility-nodes.txt").write_text(
        "\n".join(str(n.attrib) for n in visible_nodes)
    )
    # UIAutomator XML exposes accessibility clickability but does not serialize
    # Compose CustomAccessibilityAction/onClick labels. Match the five action
    # circles by their compact clickable bounds, not by invisible action labels.
    action_nodes = [n for n in visible_nodes
                    if n.attrib.get("clickable") == "true"
                    and n.attrib.get("text") == "No date"]
    (OUT / "accessibility-actions-before.txt").write_text(
        "\\n".join(str(n.attrib) for n in action_nodes) + "\\n"
    )
    if len(action_nodes) != 5:
        raise RuntimeError(f"Expected five clickable day actions, found {len(action_nodes)}")
    report.append("PASS: five clickable habit actions exposed")
    # Compose pointerInput actions may not expose an Android accessibility
    # toggle state. Use the rightmost daily action inside the Meditation card.
    label = next(n for n in visible_nodes if n.attrib.get("text") == "Meditation")
    lx, ly = center(label)
    # Five-day habit card: action circles are right aligned, below title/history.
    # Record the chosen coordinates for review; never claim success without count.
    bounds = list(map(int, re.findall(r"\d+", label.attrib["bounds"])))
    screen_width = int(adb("shell", "wm", "size").decode().strip().split("x")[0].split()[-1])
    x, y = screen_width - 150, ly + 165
    (OUT / "progress-toggle-target.txt").write_text(
        f"label_bounds={bounds}; screen_width={screen_width}; long_press=({x},{y})\n"
    )
    adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "1100")
    time.sleep(3)
    assert_count("1 of 1 completed")
    screenshot("progress-one-of-one.png")
    completed_nodes = nodes()
    # Progress completion can shift the card vertically, and the completed
    # day replaces "No date" with a timestamp. Compare horizontal bounds,
    # which identify the same five day columns without relying on labels.
    def horizontal_bounds(node):
        bounds = node.attrib.get("bounds", "")
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        return (int(match.group(1)), int(match.group(3))) if match else None

    action_columns = {horizontal_bounds(n) for n in action_nodes}
    completed_actions = [n for n in completed_nodes
                         if n.attrib.get("clickable") == "true"
                         and horizontal_bounds(n) in action_columns]
    (OUT / "accessibility-actions-after.txt").write_text(
        "\\n".join(str(n.attrib) for n in completed_actions) + "\\n"
    )
    observed_columns = {horizontal_bounds(n) for n in completed_actions}
    if observed_columns != action_columns:
        raise RuntimeError(f"Day action columns changed after completion: expected {action_columns}, found {observed_columns}")
    report.append("PASS: five clickable habit actions retained after completion")
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(4)
    assert_count("1 of 1 completed")
    # UIAutomator can observe Compose semantics before the first rendered frame.
    # Allow the resumed activity to draw before capturing visual evidence.
    time.sleep(3)
    screenshot("progress-relaunch.png")
    # Verify undo and persistence independently of the original completion.
    # Reuse the selected day column and derive its current vertical center
    # from the relaunched hierarchy instead of stale absolute coordinates.
    relaunched_actions = [n for n in nodes()
                          if n.attrib.get("clickable") == "true"
                          and horizontal_bounds(n) in action_columns]
    target_column = min(action_columns, key=lambda col: abs((col[0] + col[1]) // 2 - x))
    candidates = [n for n in relaunched_actions
                  if horizontal_bounds(n) == target_column]
    if not candidates:
        raise RuntimeError("Cannot locate completed day action after relaunch")
    # Use the candidate nearest the card label; duplicate semantics can
    # expose more than one node at the same horizontal coordinate.
    relaunched_label = find("Meditation")
    _, label_y = center(relaunched_label)
    target = min(candidates, key=lambda n: abs(center(n)[1] - (label_y + 165)))
    ux, uy = center(target)
    adb("shell", "input", "swipe", str(ux), str(uy), str(ux), str(uy), "1100")
    time.sleep(3)
    assert_count("0 of 1 completed")
    screenshot("progress-undone.png")
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(5)
    assert_count("0 of 1 completed")
    screenshot("progress-undo-relaunch.png")
    report.append("PASS: completion and undo persisted across relaunch")
except Exception as exc:
    report.append("FAIL: " + str(exc))
    try:
        screenshot("progress-diagnostic.png")
        (OUT / "progress-hierarchy.xml").write_bytes(adb("exec-out", "cat", "/sdcard/window.xml"))
    except Exception:
        pass
finally:
    (OUT / "progress-check.txt").write_text("\n".join(report) + "\n")
    print("\n".join(report))
    if any(line.startswith("FAIL:") for line in report) or sum(line.startswith("PASS:") for line in report) != 8:
        raise SystemExit(1)
