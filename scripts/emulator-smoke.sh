#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-evidence
# Include the checked-in widget preview in the evidence archive for visual review.
cp feature-widgets/src/main/res/drawable/preview_today_widget.png emulator-evidence/widget-preview.png
chmod +x ./gradlew
./gradlew --no-daemon :app:assembleDebug

echo "Available APK outputs:"
find app/build/outputs -maxdepth 6 -type f -print | sort | head -n 80 || true
mapfile -t APKS < <(find app/build/outputs -type f -name '*.apk' | sort)
if [ "${#APKS[@]}" -ne 1 ]; then
  echo "::error::Expected exactly one APK, found ${#APKS[@]}"
  exit 1
fi
APK="${APKS[0]}"
echo "Using APK: $APK"
adb install -r "$APK"
# Debug application ID is defined by app/build.gradle: applicationId + .debug suffix.
PACKAGE="com.nitinjadhav.habitflow.debug"
adb shell pm path "$PACKAGE" > emulator-evidence/installed-package.txt
test -s emulator-evidence/installed-package.txt
echo "Installed package: $PACKAGE"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1
sleep 8
adb shell pidof "$PACKAGE" | tee emulator-evidence/app-pid.txt
# Require real populated-dashboard evidence; incomplete progress checks must fail CI.
timeout --signal=TERM --kill-after=10s 4m python3 -u scripts/emulator-progress-evidence.py
# Restore the app to the foreground for the existing smoke screenshots.
adb shell am force-stop "$PACKAGE" || true
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1
sleep 3
# Screenshot evidence is best-effort; never block the required database tests.
# exec-out avoids writing to /sdcard and a separate adb pull transfer.
SCREENSHOT_OK=false
for attempt in 1 2 3; do
  echo "Capturing emulator screenshot (attempt $attempt/3)"
  if timeout --signal=TERM --kill-after=5s 20s adb exec-out screencap -p > emulator-evidence/launch.png; then
    if [[ -s emulator-evidence/launch.png ]] && [[ "$(head -c 4 emulator-evidence/launch.png | od -An -tx1 | tr -d '[:space:]')" == "89504e47" ]]; then
      SCREENSHOT_OK=true
      break
    fi
    echo "::warning::Screenshot was empty or not a PNG"
  else
    echo "::warning::Screenshot capture failed on attempt $attempt"
  fi
  sleep 2
done
if [[ "$SCREENSHOT_OK" != "true" ]]; then
  rm -f emulator-evidence/launch.png
  echo "::warning::Could not capture screenshot; continuing with required tests"
fi
# Capture the launcher/home screen separately to review the installed app icon.
# This is visual evidence only: do not fail database smoke tests on screenshot issues.
adb shell input keyevent KEYCODE_HOME || true
sleep 3
for attempt in 1 2 3; do
  echo "Capturing home screen screenshot (attempt $attempt/3)"
  if timeout --signal=TERM --kill-after=5s 20s adb exec-out screencap -p > emulator-evidence/home-screen.png; then
    if [[ -s emulator-evidence/home-screen.png ]] && [[ "$(head -c 4 emulator-evidence/home-screen.png | od -An -tx1 | tr -d '[:space:]')" == "89504e47" ]]; then
      break
    fi
  fi
  rm -f emulator-evidence/home-screen.png
  sleep 2
done
# Open Android's app-info page for this exact package. Unlike a launcher swipe,
# this gives a deterministic screen containing the installed app's icon and label.
if adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d "package:$PACKAGE"; then
  sleep 8
  adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" > emulator-evidence/app-info-activity.txt || true
  if timeout --signal=TERM --kill-after=5s 20s adb exec-out screencap -p > emulator-evidence/package-app-info.png; then
    if [[ ! -s emulator-evidence/package-app-info.png ]] || [[ "$(head -c 4 emulator-evidence/package-app-info.png | od -An -tx1 | tr -d '[:space:]')" != "89504e47" ]]; then
      rm -f emulator-evidence/package-app-info.png
    fi
  else
    rm -f emulator-evidence/package-app-info.png
  fi
fi
adb shell input keyevent KEYCODE_HOME || true
sleep 2
# The launcher may not show the icon on the default home page; capture app drawer.
adb shell input swipe 500 1700 500 400 400 || true
sleep 2
if timeout --signal=TERM --kill-after=5s 20s adb exec-out screencap -p > emulator-evidence/app-drawer.png; then
  if [[ ! -s emulator-evidence/app-drawer.png ]] || [[ "$(head -c 4 emulator-evidence/app-drawer.png | od -An -tx1 | tr -d '[:space:]')" != "89504e47" ]]; then
    rm -f emulator-evidence/app-drawer.png
  fi
else
  rm -f emulator-evidence/app-drawer.png
fi
timeout --signal=TERM --kill-after=5s 15s adb logcat -d -t 1500 > emulator-evidence/logcat.txt 2>/dev/null || true
echo "Launch smoke check passed" | tee emulator-evidence/launch-result.txt
# Run the new accessibility API test first: the legacy database suite can
# stall on this emulator, and must not prevent collecting ACTION_CLICK evidence.
echo "Running accessibility ACTION_CLICK instrumentation test"
# Build the test APK without invoking Gradle's connected test orchestrator, which
# has hung while reporting "15 Tests 0/1 completed" on this runner.
timeout --signal=TERM --kill-after=30s 6m ./gradlew --no-daemon :feature-dashboard:assembleDebugAndroidTest
TEST_APK="feature-dashboard/build/outputs/apk/androidTest/debug/feature-dashboard-debug-androidTest.apk"
test -s "$TEST_APK"
# The dashboard test APK targets SDK 21 (legacy module configuration).
# Android 14+ blocks installation by default; this test-only override does not
# alter the production app or its target SDK.
adb install --bypass-low-target-sdk-block -r "$TEST_APK"
# Explicit instrumentation class filter guarantees only the ACTION_CLICK test
# runs, rather than the entire dashboard instrumentation suite.
timeout --signal=TERM --kill-after=15s 3m adb shell am instrument -w \\
  -e class com.ofalvai.habittracker.feature.dashboard.AccessibilityActionTest \\
  com.ofalvai.habittracker.feature.dashboard.test/com.ofalvai.habittracker.core.testing.HiltTestRunner \\
  | tee emulator-evidence/accessibility-instrumentation.txt
# am instrument may exit 0 even when tests fail: require exactly one success.
grep -Eq '^OK \\(1 test\\)' emulator-evidence/accessibility-instrumentation.txt

echo "Starting bounded database instrumentation tests (6 minute limit)"
timeout --signal=TERM --kill-after=15s 6m ./gradlew --no-daemon :core-database:connectedDebugAndroidTest
if [[ "${RUN_COMPOSE_UI:-false}" == "true" ]]; then
  echo "Running optional Compose dashboard instrumentation tests"
  if [[ -n "${COMPOSE_TEST_METHOD:-}" ]]; then
    echo "Running isolated Compose test: $COMPOSE_TEST_METHOD"
    timeout --signal=TERM --kill-after=30s 8m ./gradlew --no-daemon :feature-dashboard:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.ofalvai.habittracker.feature.dashboard.AddHabitTest#$COMPOSE_TEST_METHOD"
  else
    timeout --signal=TERM --kill-after=30s 8m ./gradlew --no-daemon :feature-dashboard:connectedDebugAndroidTest
  fi
else
  echo "Compose dashboard tests skipped in core smoke run (known emulator hang)"
fi
echo "Installed and launched $PACKAGE" | tee emulator-evidence/result.txt
