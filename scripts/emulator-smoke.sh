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
PACKAGE=$(aapt dump badging "$APK" | awk -F"'" '/^package: name=/{print $2; exit}')
test -n "$PACKAGE"
echo "Installed package: $PACKAGE"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1
sleep 8
adb shell pidof "$PACKAGE" | tee emulator-evidence/app-pid.txt
adb shell screencap -p /sdcard/habitflow-smoke.png
adb pull /sdcard/habitflow-smoke.png emulator-evidence/launch.png
adb logcat -d -t 1500 > emulator-evidence/logcat.txt
echo "Launch smoke check passed" | tee emulator-evidence/launch-result.txt
timeout --signal=TERM --kill-after=30s 10m ./gradlew --no-daemon :core-database:connectedDebugAndroidTest
timeout --signal=TERM --kill-after=30s 8m ./gradlew --no-daemon :feature-dashboard:connectedDebugAndroidTest
echo "Installed and launched $PACKAGE" | tee emulator-evidence/result.txt
