# Cube Tutor (魔方复原课)

Read the cube in your hand with the headset camera (v0.1.1, design v1.6: look down at it and turn it once around; if a
side is not recognised, hold it squarely to the camera; if that fails or the camera cannot be used, tap the colours) —
or use a scrambled virtual one; a twin virtual cube floats beside
you and the seven-stage layer-by-layer lesson walks you through every move, each with a plain-words caption and a short
reason. One controller is enough: tap a handle beside a row / column / slice (more than one in the same row works),
push the stick one notch = a quarter turn. PICO OS 6, Kotlin, PICO Spatial SDK 6.1.9, package `com.kejing.cubetutor`.

Container **STAGE_MIXED** (Full Space, passthrough; manifest `pico.spatial.stage.style=1`), design gate B v1.4. All 2D
content is ONE system Planar window (640 × 600 dp ≈ 0.51 × 0.48 m at the measured 0.8 mm/dp) with the system TabBar
(开始 / 复原课 / 自由玩 / 设置); it opens through the level gate (|pitch| ≤ 10° for 300 ms). The cube rig is placed at
that moment 43° left of the head heading, 0.60 m away, 0.15 m below the eyes, turned to face you (yaw only). Permission:
CAMERA only (asked the first time you tap Read my cube; SpatialML readback mode). No network.

## Cube reader (v0.1.1, design v1.6 cameraCapability / readFlow, G6 three tiers — stays in Full Space)

Approach ported from `Pico-Developer/SpatialML-Samples/samples/rubics_cube` (CPU readback + ScanManager / ColorClassifier,
no model), not its OpenXR layer:

```
FrameSource (platform/camera)  CameraFrameSource: SpatialML session 0x0x0 DISABLED, rectifiedVSTAccess right eye
                               640x480 RGB -> global tensor, submit + readbackContentSuspend every 160 ms (IO thread,
                               3 s / 8 s timeouts, 3 errors or 8 s without a non-zero frame = unavailable). Log prefix CAM.
                               InjectedFrameSource: debug pictures at the same rate. Never the camera on the emulator.
ScanBridge (ui)                one frame in flight -> GridFinder on a worker thread -> ScanSession on main -> CtController
domain/scan (pure, JVM tests)  GridFinder (tiles between dark plastic / seams / colour edges, 3x3 lattice that may put
                               several points in one merged same-colour region, gray-world white balance + brightest
                               channel = 220), ColorModel (Lab, 6 headset-measured refs, each face read against its
                               own centre, confirmed centres calibrate), Stabilizer (same 9 colours 0.6 s), ScanSession
                               (repeat guard, red/orange swap, tier 2 at 4 s, tier 3 at 30 s), CubeAssembler (balanced
                               6x9 grouping, U/D turn fix, amber squares)
```

User addenda 2026-11-11 (design-package userAddenda): faces are identified by their centre colour (any order; standard
scheme W–Y R–O B–G, `Inference.faceOf`); after every face `domain/scan/Inference` solves the unread stickers (piece
backtracking + parity) — unique → stop and fill them in (usually after 5 faces), several → ask for the face that
separates them, none → keep scanning; then the **confirm view** (ReadMode.CONFIRM = S08 before the virtual cube: cube
net in the window, inferred squares striped / slow blink in 3D, unsure amber; tap a 3D square to change it, a centre
can't take another centre's colour; edits re-run inference) → 对，就是这样 → legality → S08 ready / S07. The camera
looks only inside the **alignment frame**: a world panel placed every frame 0.33 m ahead, 8° below the line of sight,
box 160 dp = 5.7 cm (FRAME_K); ROI `GridFinder.Roi.DEFAULT` until the headset spike calibrates it (`roi=x,y,w,h`).

User 2026-11-17 (after the headset test): **per-face confirm** — a face is taken only when held still 0.9 s, square
to the camera (GridFinder.squareOn) and not moving; then ReadMode.FACE_OK (= S03): the camera pauses, the window
shows the nine squares big (amber = unsure), tap one to change it (S06), [对，下一面] keeps it (ScanSession.confirmed
→ ColorModel.learn from all nine) and runs inference, [重扫这一面] forgets it (ScanSession.forget). Debug `faceok=1|auto|0`,
`facerescan=1`; picture-set tours use `faceok=auto`.
**The side read is the side asked for** (user 2026-11-17, replaces "any order by centre colour"): its centre = the
scheme colour, the measured centre only becomes that colour's reference (`ColorModel.withCentreAs`); a centre clearly of
another unread side → Guard.WRONG_SIDE (not taken); the same side again → Guard.REPEAT by Lab distance of the nine
squares; changing the centre at S03 moves the read to that side (`CtController.moveFace`, `ScanSession.moveTo`).
Detection runs on ROI FULL (middle 80 %); a lattice with full-size tiles continuing past an edge is rejected (slid).

Tier ① auto → ② after 4 s the big prompt "face the headset camera" (window + scan card) → ③ after 30 s "tap this side"
(S04 for that side, then back to the camera); camera denied / failing / emulator → S05 → S04 for the rest, sides already
read stay. Headset spike (cameraCapability.spike) not run yet: watch `CAM session ok`, `CAM frame n`, `CAM no frames`.


Design package (binding): `../spatial-app-factory/apps/cube-tutor/design-package.json` (states S00–S30, cubeSpec,
input, spatialLayout, content). Reference repo: LineOfSightPilot (window / level gate / controller code), board-input
template (3D hit testing), five-stones sound set (Kenney UI cues).

## Structure

```
app/src/main/java/com/kejing/cubetutor/
  Main.kt                DefaultStage (Mixed) -> CtScreen; WindowContainer ControlPanel -> CtWindow
  domain/                pure Kotlin, JVM-tested
    Cube.kt              54 facelets URFDLB in the DISPLAY frame, Turn (axis + layers + ±90°), Moves notation, Perms
    Legality.kt          ① colour count ② pieces ③ parity (design cubeSpec.legality)
    Solver.kt            7-stage tutor: daisy+cross, corners (R U R' U'), belt, yellow cross, edges, corners home,
                         twist; small uniform-cost searches over each stage's own macros; Scrambler
    Handles.kt           Row / Handle / Selection / Push, tap (multi-select, row switch), turnFor / handleFor,
                         PushDetector (0.6 / 0.3), RigLayout (43° / 0.60 m / 0.15 m), SteadyGate (D29 re-center)
    Lessons.kt           lesson copy (names, goals, whys, demos, captions, reasons) zh + en; demoStart(k)
    LevelGate.kt Layout.kt Geometry.kt Vec3.kt   (from LineOfSightPilot)
  platform/              CtLog (tag CubeTutor), Store (prefs + save.json), Sound (SoundPool, one instance per event),
                         input/Controllers (stick of either hand, trigger/A, B, ray), tracking/HeadTracker
  ui/
    CtUiState.kt         state + designState ("S10+S17")
    CtController.kt      ALL behaviour: Ui events -> state, lesson planning (worker thread), animations queue
    CtScreen.kt          wires controller, scene, lifecycle; opens the window
    window/              CtWindow (WindowCtl: level gate, re-center, chip; the window content), MainWindowTemplate
    components/          Pages.kt (Home / Learn / Play / Settings), CtStage (SpatialView + boardInput + chip / hint),
                         Strings.kt, TextFit.kt
    scene/CubeScene.kt   rig -> presentRoot (3/4 view) -> cubeRoot -> 26 cubies + 54 sticker tiles; handles, bar,
                         tray, ghost layer, outline, controller ray; turn animation; Kit (meshes / materials / labels)
    scene/Labels.kt      run-time bitmaps (handles, arrows, strips)   scene/PanelRig.kt  pooled attachment panels
    input/BoardInput.kt  factory template; input/CubeTargets.kt  entity names <-> targets (pure, unit-tested)
app/src/debug/           DebugCommandReceiver + DebugCommands
app/src/main/assets/sfx  turn / turn_2 / turn_3 / whole (CC0 recordings), select / correct / nudge (Kenney CC0),
                         stage / solved (synthesised UI chimes) — factory apps/cube-tutor/build/sound-sources.md
tools/                   dev.sh, tour.sh, strings.py (single source of copy), make_icon.py, cut_turns.py, make_cues.py
```

Conventions: Stage metres, −Z ahead. Rig space: origin = cube centre, +X right, +Y up, +Z towards you. The cube state is
kept in the display frame: a whole-cube turn permutes facelets, the cube never rotates as a whole, so handle "Right"
always means the column on your right (design joystickMapping). Data flows one way: window keys / 3D hits / stick /
debug -> `CtController.onEvent`; the frame system plays `CtController.anims` and reads the state.

## Build, run (mac1)

```bash
tools/dev.sh build && tools/dev.sh install && tools/dev.sh relaunch   # DEV=emulator-5554 by default
DEV=PB324XJGL2090081G tools/dev.sh install                            # headset first (factory rule)
./gradlew --offline testDebugUnitTest                                 # domain + hit-replay tests (1000-scramble solve ~70 s)
tools/tour.sh OUTDIR zh|en                                            # all state screenshots
python3 -I tools/strings.py ; python3 -I tools/make_icon.py --png ../spatial-app-factory/apps/cube-tutor/icon.png
```

JAVA_HOME = primer JDK 21 (dev.sh sets it). Material gate: `/usr/bin/grep -rEf ../spatial-app-factory/bin/material-grep.txt
--include='*.kt' --include='*.kts' app/src` prints nothing.

## Debug broadcast (debug builds)

`tools/dev.sh cmd 'k=v k=v …'` = `adb shell "am broadcast -a com.kejing.cubetutor.DEBUG -p com.kejing.cubetutor --es cmd '…'"`.
Pairs run left to right; each sends the window's own events, taps the scene's own targets or plays the lesson's own
steps. `STATE Sxx` is logged on every change.

| command | effect |
|---|---|
| `tab=home|learn|play|settings` `state=S01|S02|S16|S18|S20|S21` | TabBar / macro |
| `read=1` `face=N` `fill=N` `fillall=1` `paint=W` `facedone=1` | start reading (emulator without pictures → S05 → S04); S04: go to face, fill N cells correctly, paint colour, 这面填好了 |
| `scanimg=scan/setA/face2.png [noise=0.3]` `scanset=scan/setA [hold=1]` `scanfake=WRGBOY…` `scanoff=1` | v1.6 reader on injected pictures (debug assets `scan/setA|B|C|Bad`, or a path under files/): one picture / six in order / fixed colours / back to the camera |
| `scanpause=1` `scanhold=1` `tier=2|3` | screenshots: hold the gold "hold still" frame / keep S03 (and S05) up / jump to the G6 tier-2 / tier-3 prompts |
| `camerafail=1` `camdeny=1` `askcam=1` `paintface=1` `paintall=1` `backscan=1` | S05 (failed / denied), 再问一次权限, 这面点颜色, 不扫了全部点颜色, 回去扫 |
| `confirm=1` `fixcancel=1` `roi=x,y,w,h|full|default` | 对，就是这样 (confirm view) / cancel S06 / detection ROI |
| `loadamber=2` `amberok=1` `amberfix=1` `reviewdone=1` `dumpframe=1` `blank=1` | S08 with unsure squares, 都对 / 去改 (S06) / 改好了; save the last camera frame to files/scan-dump/; D44 blank measure of the window (`BLANK …` log) |
| `bad=count|piece|parity` `load=<54 chars>` | S07 with each failure kind / any cube through the reading check |
| `tap=sticker:F2` `fix=R` `readagain=1` `fromstart=1` | S06 fix a cell / S07 keys |
| `ready=1 [seed=N]` | a legal read cube -> S08 |
| `lesson=L2 step=2 mode=real|virtual [at=y]` | lesson at stage k, step n (or the first step with move `at`) |
| `do=1` `turned=1` `again=1` `undo=1` `why=1` `demoStep=N` `whyok=1` `done=1` `next=1` `notsame=1` `recompute=1` | lesson keys / S13 / S14 / S12 |
| `tap=handle:row+1|row0|row-1|rowAll|col-1…|depth+1…` `tap=dpad:a|b` `push=up|down|left|right` `clear=1` | handles, bar arrows, stick push, B |
| `turn=R'` | any turn through the user path |
| `scramble=15 seed=7` `level=6` `solved=1` | free play / S15 |
| `learnvirtual=1` `replace=yes|no` | S19 confirm |
| `closepanel=1` `openpanel=1` `recall=1` | S22 window × / chip / S30 re-center |
| `drag=start` `drag=move dx= dy= dz=` `drag=end` | S29 tray drag |
| `lang=zh|en|system` `mute=0|1` `volume=N` `coach=1` `coachok=1` `coach_reset=1` `reset=1` | settings / first-run |
| `freeze=1|0` `view=DEG` `fit` `where` `instances` | hold an animation / place the rig as if the head turned / diagnostics |

## Screenshot list

`tools/tour.sh` covers S01 (+coach), S02 (gold frame, tier 2, tier 3, repeat — shot looking down 35° at the hands),
S03, S08-confirm (+edit, centre clash, bad set), S04 (face 2, face 5), S05 (denied, failed), S06 (+review), S07 (scanned bad set + ×3), S08 (scanned, amber), S08, S09 (L2, L3, y step), S10
(+picked), S11, S12, S13, S14, S15 (lesson, free play), S16, S17, S18, S19, S20, S21, S22 (+reopen), S23 (+whole), S24,
S25, S26, S27, S29. Not on the emulator: S00 (0.5 s), S28 (head pitch cannot be injected; LevelGateTest), S30 steady head
(SteadyGate test; `recall=1` runs the path). Synthetic scan pictures: `python3 -I tools/make_scan_sets.py`.

## Platform facts found here

- Headset camera spike 2026-11-17 (swan, right-eye rectified VST via SpatialML readback): session in 0.15–0.25 s,
  640 x 480 RGB every ~163 ms; fails with `SSMR backend exception` / system `open camera fail -99` while the headset
  is off the head and asleep (camera off) — the app falls to S05 as designed. A cube held at the alignment frame is
  ~70 px wide around (0.45, 0.6) of the frame (inside ROI DEFAULT). Warm dim light: mean ≈ 64, blue reads grey and red
  reads orange without white balance. The user's cube is **stickerless** (1 px seams): same-colour neighbours merge, so
  GridFinder accepts merged regions. Real frames + truth: `app/src/test/resources/scan-real/2026-11-17`, RealFrameTest
  (12 / 14 found, every front view right up to red / orange). Logcat `SCAN frame n grid=… <GridFinder stats>` every
  30 frames; debug builds dump every 20th camera frame (max 40) to files/scan-dump/.

- v0.1.2: in Full Space the **system** draws the controller ray and delivers the trigger as a tap (board-input). The app
  used to draw its own ray and turn its own trigger into a tap → two rays and one press toggling a handle twice. Now:
  no app ray, no app trigger tap; stick + B still read from ControllerTrackingProvider; `TapDeduper` drops a repeat of
  the same target within 350 ms on the single 3D-hit path (`CubeScene.onTarget`).

- Emulator camera straight ahead does not see the rig at 43° left; `tools/tour.sh` turns the camera in place 25° left
  (`pico-cli capture screenshot --intent-json`, restored after each shot). The default straight-ahead view only shows the
  depth handles and part of the cube (build/default-view.png).
- Window: `LocalPhysicalLengthConverter` = 0.8 mm/dp, so 640 dp = 0.51 m; at 1.75 m its left edge is ~8.3°, the rig's
  right edge 20.4° -> ~12° clear (design asks ≥ 10°).
- Emulator: `ControllerTrackingProvider` = DEVICE_NOT_SUPPORTED (no stick, no app ray): the bar's arrows are the path.
