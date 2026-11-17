#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-evidence
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
# Screenshot evidence is best-effort; never block the required database tests.
# exec-out avoids writing to /sdcard and a separate adb pull transfer.
SCREENSHOT_OK=false
for attempt in 1 2 3; do
  echo "Capturing emulator screenshot (attempt $attempt/3)"
  if timeout --signal=TERM --kill-after=5s 20s adb exec-out screencap -p > emulator-evidence/launch.png; then
    if [[ -s emulator-evidence/launch.png ]] && [[ "$(head -c 4 emulator-evidence/launch.png | od -An -tx1 | tr -d ' \\n')" == "89504e47" ]]; then
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
timeout --signal=TERM --kill-after=5s 15s adb logcat -d -t 1500 > emulator-evidence/logcat.txt 2>/dev/null || true
echo "Launch smoke check passed" | tee emulator-evidence/launch-result.txt
timeout --signal=TERM --kill-after=30s 10m ./gradlew --no-daemon :core-database:connectedDebugAndroidTest
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
