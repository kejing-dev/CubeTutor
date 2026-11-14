#!/usr/bin/env bash
# Gate evidence (ui_gates: default-view, settings-firstscreen, newgame-confirm, exit-recents) + first screen, all after
# the last build. DEV = the device (the headset is kept awake meanwhile).
#   DEV=PB324XJGL2090081G tools/evidence.sh
cd "$(dirname "$0")/.."
DEV="${DEV:-emulator-5554}"; export DEV
B=../spatial-app-factory/apps/cube-tutor/build; mkdir -p "$B"
case "$DEV" in emulator-*) ;; *) (for i in $(seq 1 120); do adb -s "$DEV" shell input keyevent KEYCODE_WAKEUP; sleep 2; done) >/dev/null 2>&1 & ;; esac
shot() { pico-cli capture screenshot -d "$DEV" -o "$B/$1.png" --replace >/dev/null 2>&1; echo "$1 rc=$? $(tools/dev.sh log STATE 1 | sed 's/.*STATE //')"; }
adb -s "$DEV" logcat -c
tools/dev.sh relaunch >/dev/null
for i in $(seq 1 40); do tools/dev.sh log PLACE 1 | grep -q PLACE && break; sleep 2; done
tools/dev.sh log "PLACE|STARTUP" 4
sleep 5;                                                                        shot default-view
tools/dev.sh cmd 'coachok=1 lang=zh' >/dev/null; sleep 3;                       shot first-screen
tools/dev.sh cmd 'tab=settings' >/dev/null; sleep 3;                            shot settings-firstscreen
tools/dev.sh cmd 'lesson=L3 mode=virtual' >/dev/null; sleep 3
tools/dev.sh cmd 'tab=home learnvirtual=1' >/dev/null; sleep 3;                 shot newgame-confirm
tools/dev.sh cmd 'replace=no' >/dev/null; sleep 2
tools/dev.sh cmd 'quit=ask' >/dev/null; sleep 2
tools/dev.sh cmd 'quit=yes' >/dev/null; sleep 4
adb -s "$DEV" shell dumpsys activity recents > "$B/exit-recents.txt"
echo "recents rows with the app: $(grep 'Recent #' "$B/exit-recents.txt" | grep -c cubetutor)"
tools/dev.sh log EXIT 2
