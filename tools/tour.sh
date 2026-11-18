#!/usr/bin/env bash
# Screenshot tour of the design states (AGENTS.md "Screenshot list"). The cube rig sits 43° left of the window, outside
# the emulator camera's view straight ahead: shots turn the camera in place 25° left (pico-cli camera intent; the
# camera is restored after each shot). Each shot prints the STATE the app logged.
#   tools/tour.sh OUTDIR [zh|en]
cd "$(dirname "$0")/.."
OUT="${1:?outdir}"; L="${2:-zh}"; mkdir -p "$OUT"
DEV="${DEV:-emulator-5554}"
PLAN="$OUT/.left.json"
echo '{"version":1,"sourceText":"turn left 25 degrees in place","steps":[{"kind":"in-place","horizontalDegrees":-25,"verticalDegrees":-8}]}' > "$PLAN"
c() { tools/dev.sh cmd "$1" >/dev/null; sleep "${2:-3}"; }
# looking down at your hands (35°): the scan card sits 14° above that gaze while reading
DOWN="$OUT/.down.json"
echo '{"version":1,"sourceText":"look down 35 degrees at the cube in your hands","steps":[{"kind":"in-place","horizontalDegrees":0,"verticalDegrees":-35}]}' > "$DOWN"
# NOSHOT=1: walk the states and only log the D44 blank measure (BLANK lines), no screenshots
if [ -n "$NOSHOT" ]; then
  ahead() { sleep 1; tools/dev.sh cmd 'blank=1' >/dev/null; sleep 1; echo "$1 -> $(tools/dev.sh log 'BLANK' 1 | sed 's/.*BLANK //')"; }
  shot() { ahead "$@"; }
else
ahead() { tools/dev.sh cmd 'blank=1' >/dev/null; pico-cli capture screenshot --intent-json @"$DOWN" --device "$DEV" --out "$OUT/$1.png" >/dev/null 2>&1 || echo "shot $1 failed"; echo "$1 -> $(tools/dev.sh log 'STATE' 1 | sed 's/.*STATE //')"; }
shot() { tools/dev.sh cmd 'blank=1' >/dev/null; pico-cli capture screenshot --intent-json @"$PLAN" --device "$DEV" --out "$OUT/$1.png" --format json >/dev/null 2>&1 || echo "shot $1 failed"; echo "$1 -> $(tools/dev.sh log 'STATE' 1 | sed 's/.*STATE //')"; }
fi
c "reset=1 lang=$L" 5;                       shot S01-coach
c "coachok=1" 2;                             shot S01
# v1.6 reading: injected pictures replace the camera on the emulator (same reader code)
c "scanoff=1 scanimg=scan/setA/face1.png scanpause=1" 1; c "read=1" 5;   ahead S02; shot S02-rig
c "tier=2" 3;                                ahead S02-tier2
c "tier=3" 3;                                ahead S02-tier3
c "scanpause=0 scanhold=1" 4;                ahead S03; shot S03-rig; shot S03-confirm
c "tap=sticker:F2" 2;                       shot S03-fix; c "fixcancel=1" 1
c "scanhold=0 faceok=1" 7;                  ahead S02-repeat
c "scanset=scan/setA hold=1 faceok=auto" 1; c "fromstart=1" 22; shot S08-confirm
c "tap=sticker:F1" 3;                        shot S06-confirm-edit
c "tap=sticker:F5 fix=W" 3;                  shot S06-centre-clash
c "fixcancel=1 confirm=1" 5;                 shot S08-scanned
c "scanoff=1 scanhold=1 camdeny=1" 1; c "fromstart=1" 3; shot S05-denied
c "camdeny=0 camerafail=1" 1; c "fromstart=1" 3; shot S05-failed
c "scanhold=0" 1; c "fromstart=1" 4;         c "face=2 fill=4" 3; shot S04-face2
c "face=5" 3;                                shot S04-face5
c "scanoff=1 loadamber=2" 4;                 shot S08-amber
c "amberfix=1" 3;                            shot S06-review
c "scanset=scan/setBad hold=1 faceok=auto" 1; c "fromstart=1" 22; shot S08-confirm-bad
c "confirm=1" 4;                             shot S07-scanned
c "scanoff=1 bad=count" 3;                   shot S07-count
c "tap=sticker:F2" 3;                        shot S06
c "bad=piece" 3;                             shot S07-piece
c "bad=parity" 3;                            shot S07-parity
c "ready=1" 6;                               shot S08
c "lesson=L2 step=2 mode=real" 5;            shot S09-L2
c "lesson=L3 step=1 mode=real" 5;            shot S09-L3
c "lesson=L2 mode=real at=y" 5;              shot S09-y
c "lesson=L2 step=2 mode=virtual" 5;         shot S10
c "tap=handle:col+1" 3;                      shot S10-picked
c "do=1 freeze=1" 2;                         shot S11
c "freeze=0 lesson=L2 step=2 mode=virtual" 4
c "tap=handle:row+1 push=right" 4;           shot S27
c "recompute=1" 2;                           shot S12
c "lesson=L2 step=2 mode=virtual why=1" 4;   c "demoStep=2" 3; shot S13
c "whyok=1 lesson=L1 mode=real done=1" 5;    shot S14
c "notsame=1" 4;                             shot S04-reconcile
c "lesson=L7 done=1" 6;                      shot S15-lesson
c "reset=1 coachok=1 lang=$L tab=learn" 4;           shot S18
c "scramble=15 seed=7" 4;                    shot S16
c "tap=handle:col+1" 3;                      shot S17
c "tap=handle:row+1" 2; c "tap=handle:row0" 3; shot S23
c "tap=handle:rowAll" 3;                     shot S23-whole
c "tap=handle:col+1" 2;                      shot S24
c "clear=1" 2; c "push=left" 1;              shot S26
c "tap=handle:col+1 push=up freeze=1" 2;     shot S25
c "freeze=0" 2
c "lesson=L3 mode=virtual" 4; c "learnvirtual=1" 3; shot S19
c "replace=no tab=play" 2; c "solved=1" 6;   shot S15-play
c "tab=settings" 3;                          shot S20
c "quit=ask" 3;                              shot S21
c "quit=cancel closepanel=1" 4;              shot S22
c "openpanel=1" 6;                           shot S22-reopened
c "drag=start drag=move dx=0.15 dz=0.1" 3;   shot S29
c "drag=end" 3;                              shot S29-released
