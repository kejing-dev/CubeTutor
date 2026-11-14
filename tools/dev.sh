#!/usr/bin/env bash
# Cube Tutor dev helper (mac1): build / install / relaunch / cmd / shot / log on one device.
#   DEV=emulator-5554 tools/dev.sh build|test|install|relaunch|cmd 'k=v …'|shot NAME [DIR]|log PATTERN [N]|crash
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.pico/primer-cli/jdk/jdk-21.0.12.1+1/Contents/Home}"
DEV="${DEV:-emulator-5554}"
PKG=com.kejing.cubetutor
APK=app/build/outputs/apk/debug/app-debug.apk
case "$1" in
  build) ./gradlew --offline -q assembleDebug ;;
  test) ./gradlew --offline -q testDebugUnitTest ;;
  install) adb -s "$DEV" install -r "$APK" | tail -1 ;;
  relaunch) adb -s "$DEV" shell am force-stop $PKG; adb -s "$DEV" shell am start -W -n $PKG/.platform.LaunchActivity | tail -2 ;;
  cmd) adb -s "$DEV" shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd '$2'" >/dev/null; echo "sent: $2" ;;
  shot) out="${3:-.}/$2.png"; pico-cli capture screenshot -d "$DEV" -o "$out" --replace --spatial --size 2560x1440 >/dev/null 2>&1; echo "$out" ;;
  log) adb -s "$DEV" logcat -d -s CubeTutor:V | /usr/bin/grep -E "$2" | tail -"${3:-40}" ;;
  crash) adb -s "$DEV" logcat -d -b crash | tail -40 ;;
  *) sed -n 2,3p "$0" ;;
esac
