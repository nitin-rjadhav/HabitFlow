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
adb shell screencap -p /sdcard/habitflow-smoke.png
adb pull /sdcard/habitflow-smoke.png emulator-evidence/launch.png
timeout --signal=TERM --kill-after=5s 15s adb logcat -d -t 1500 > emulator-evidence/logcat.txt 2>/dev/null || true
echo "Launch smoke check passed" | tee emulator-evidence/launch-result.txt
timeout --signal=TERM --kill-after=30s 10m ./gradlew --no-daemon :core-database:connectedDebugAndroidTest
if [[ "${RUN_COMPOSE_UI:-false}" == "true" ]]; then
  echo "Running optional Compose dashboard instrumentation tests"
  timeout --signal=TERM --kill-after=30s 8m ./gradlew --no-daemon :feature-dashboard:connectedDebugAndroidTest
else
  echo "Compose dashboard tests skipped in core smoke run (known emulator hang)"
fi
echo "Installed and launched $PACKAGE" | tee emulator-evidence/result.txt
