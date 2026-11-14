package com.kejing.cubetutor.ui

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.kejing.cubetutor.domain.Cube
import com.kejing.cubetutor.domain.CubeColor
import com.kejing.cubetutor.domain.Facelets
import com.kejing.cubetutor.domain.Handle
import com.kejing.cubetutor.domain.Handles
import com.kejing.cubetutor.domain.Legality
import com.kejing.cubetutor.domain.Lessons
import com.kejing.cubetutor.domain.Moves
import com.kejing.cubetutor.domain.Push
import com.kejing.cubetutor.domain.Row
import com.kejing.cubetutor.domain.Scrambler
import com.kejing.cubetutor.domain.Solver
import com.kejing.cubetutor.domain.Turn
import com.kejing.cubetutor.domain.scan.CubeAssembler
import com.kejing.cubetutor.domain.scan.FaceRead
import com.kejing.cubetutor.domain.scan.Inference
import com.kejing.cubetutor.domain.scan.GridFinder
import com.kejing.cubetutor.domain.scan.ScanSession
import com.kejing.cubetutor.platform.camera.CameraPermission
import com.kejing.cubetutor.platform.CtLog
import com.kejing.cubetutor.platform.Sound
import com.kejing.cubetutor.platform.Store
import com.kejing.cubetutor.ui.window.WindowCtl
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the user (or a debug command, or the controller's stick) can ask for. One path into [CtController]. */
sealed class Ui {
    data class TabTo(val tab: Tab) : Ui()
    object CoachOk : Ui()

    // home / reading (S01–S08, S19)
    object ReadMine : Ui()
    object LearnVirtual : Ui()
    object PlayVirtual : Ui()
    object Resume : Ui()
    data class Paint(val color: CubeColor) : Ui()
    data class TapSticker(val index: Int, val src: String) : Ui()
    object FaceDone : Ui()
    data class GoFace(val face: Int) : Ui()
    object ReconcileDone : Ui()
    object ReadFaceAgain : Ui()
    object ReadFromStart : Ui()
    data class FixColor(val color: CubeColor) : Ui()
    object FixCancel : Ui()
    object StartReal : Ui()
    object ReadyPlay : Ui()
    object ReplaceYes : Ui()
    object ReplaceNo : Ui()

    // v1.6 scanning (S02–S05, S08 amber ask)
    object PaintThisFace : Ui()
    object PaintAll : Ui()
    object BackToScan : Ui()
    object AskCameraAgain : Ui()
    object AmberOk : Ui()
    object AmberFix : Ui()
    object ReviewDone : Ui()
    object ConfirmRead : Ui()

    // the cube (handles, stick, the bar's arrows, B)
    data class TapHandle(val handle: Handle, val src: String) : Ui()
    data class PushStick(val push: Push, val src: String) : Ui()
    data class Arrow(val first: Boolean, val src: String) : Ui()
    data class ClearPick(val src: String) : Ui()

    // lesson (S09–S15, S27)
    object TurnedIt : Ui()
    object HelpTurn : Ui()
    object ShowAgain : Ui()
    object Undo : Ui()
    object Why : Ui()
    object WhyAgain : Ui()
    object WhyOk : Ui()
    object NextStage : Ui()
    object NotSame : Ui()
    object Recompute : Ui()
    object AgainRound : Ui()
    object ReadNext : Ui()
    object LearnScramble : Ui()

    // free play (S16)
    data class Level(val moves: Int) : Ui()
    object Scramble : Ui()
    object ToLesson : Ui()

    // settings (S20 / S21)
    data class SetPrefs(val why: String, val f: (Prefs) -> Prefs) : Ui()
    object Recenter : Ui()
    data class Ask(val ask: SettingsAsk) : Ui()
    object ResetYes : Ui()
    object ResetBestYes : Ui()
    object QuitYes : Ui()
}

/** An animation for the scene: a quarter turn (ms long) or an instant snap to a cube (the "why" demo, scrambles). */
sealed class Anim {
    data class Spin(val turn: Turn, val ms: Long, val whole: Boolean) : Anim()
    data class Snap(val cube: Cube, val why: String) : Anim()
}

/**
 * All behaviour (design states S00–S30, interactions I1–I29): events in, [CtUiState] out, animations queued for the
 * scene ([anims]). Turns from the handles, the stick, the bar's arrows, the lesson's 转好了 / 帮我转 and debug commands
 * all go through [userTurn] / [applyTurn] (factory rule 2026-10-29: no shortcut state writes). Main thread only.
 */
object CtController {
    private val _state = MutableStateFlow(CtUiState())
    val state: StateFlow<CtUiState> = _state

    private val main = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private var store: Store? = null
    var soundOrNull: Sound? = null
        private set
    private var activity: Activity? = null
    private var started = false
    private var whyJob: Job? = null
    private var stageJob: Job? = null

    /** Scene -> controller: a turn animation is running (input is locked, design cubeSpec.turnAnim). */
    @Volatile var animating = false

    /** Queue the scene plays in order. */
    val anims = ArrayDeque<Anim>()

    /** The scene releases trackers before the process ends (D26). */
    var quitHook: (() -> Unit)? = null

    const val TURN_MS = 250L
    const val HALF_MS = 400L
    const val DEMO_STEP_MS = 1600L

    fun start(context: Context) {
        if (started) return
        started = true
        activity = context as? Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity
        val st = Store(context.applicationContext)
        store = st
        val prefs = st.prefs()
        val snd = Sound(context.applicationContext).also { it.start(); it.setVolume(prefs.volume, prefs.muted) }
        soundOrNull = snd
        val saved = st.loadSave()
        val cube = saved?.cube?.let { Cube.parse(it) }?.takeIf { Legality.check(it) == null } ?: Cube.solved()
        set("start") { it.copy(loading = true, prefs = prefs, cube = cube, hasCube = saved?.hasCube == true && saved.cube.isNotEmpty(), coach = !prefs.coachSeen, play = it.play.copy(level = saved?.scrambleLevel ?: 15)) }
        if (saved?.lessonMode != null && !cube.isSolved) {
            // design dataModel.save: restore to "this move" and re-plan this stage
            val mode = runCatching { LessonMode.valueOf(saved.lessonMode) }.getOrDefault(LessonMode.VIRTUAL)
            set("restore") { it.copy(lesson = LessonState(mode, Solver.firstOpenStage(cube), startMs = now())) }
            startStage(Solver.firstOpenStage(cube), recomputed = false)
        }
        CtLog.i("START prefs=$prefs saved=${saved != null} cube=${cube.code()}")
    }

    fun setActivity(a: Activity?) {
        if (a != null) activity = a
    }

    /** The scene says the Stage is up (first frame with a head pose, or the no-head fallback): S00 -> S01. */
    fun onReady() {
        if (!_state.value.loading) return
        set("ready") { it.copy(loading = false) }
    }

    fun onWindow(open: Boolean, levelWait: Boolean, recentering: Boolean) {
        val s = _state.value
        if (s.windowOpen == open && s.levelWait == levelWait && s.recentering == recentering) return
        set("window open=$open level=$levelWait recenter=$recentering") { it.copy(windowOpen = open, levelWait = levelWait, recentering = recentering) }
    }

    fun onAway(away: Boolean) {
        if (_state.value.away != away) set("away=$away") { it.copy(away = away) }
    }

    fun onDragging(d: Boolean) {
        if (_state.value.dragging != d) set("drag=$d") { it.copy(dragging = d) }
    }

    private fun now() = System.currentTimeMillis()

    private var lastLogged = ""

    private fun set(why: String, f: (CtUiState) -> CtUiState) {
        val before = _state.value
        val after = f(before)
        _state.value = after
        val ds = after.designState
        if (ds != lastLogged || why.startsWith("tab") || why.startsWith("turn")) {
            lastLogged = ds
            CtLog.i("STATE $ds tab=${after.tab} why=$why")
        }
    }

    // ============================================================================================== events

    fun onEvent(e: Ui) {
        val s = _state.value
        CtLog.i("UI ${e.javaClass.simpleName}${if (e is Ui.TapHandle) " ${e.handle.id} src=${e.src}" else if (e is Ui.PushStick) " ${e.push} src=${e.src}" else if (e is Ui.TapSticker) " ${Facelets.name(e.index)} src=${e.src}" else ""} state=${s.designState}")
        when (e) {
            is Ui.TabTo -> set("tab ${e.tab}") { it.copy(tab = e.tab, ask = SettingsAsk.NONE, replaceAsk = null, hint = null) }
            Ui.CoachOk -> {
                pref("coach") { it.copy(coachSeen = true) }
                set("coachOk") { it.copy(coach = false) }
            }
            Ui.ReadMine -> if (s.lessonLive) set("replace?") { it.copy(replaceAsk = ReplaceTarget.READ) } else startRead(false)
            Ui.LearnVirtual, Ui.LearnScramble -> if (s.lessonLive) set("replace?") { it.copy(replaceAsk = ReplaceTarget.LEARN) } else learnVirtual()
            Ui.PlayVirtual -> set("play") { it.copy(tab = Tab.PLAY, ready = false) }
            Ui.Resume -> set("resume") { it.copy(tab = Tab.LEARN) }
            is Ui.Paint -> s.read?.let { r -> set("paint ${e.color}") { it.copy(read = r.copy(paint = e.color)) } }
            is Ui.TapSticker -> tapSticker(e.index)
            Ui.FaceDone -> faceDone()
            is Ui.GoFace -> s.read?.let { r -> val n = e.face.coerceIn(1, 6); set("face $n") { it.copy(read = r.copy(face = n, target = Lessons.readFaces[n - 1], failure = null, fixing = null)) } }
            Ui.ReconcileDone -> checkRead()
            Ui.ReadFaceAgain -> s.read?.let { r ->
                val f = r.failure?.suspects?.firstOrNull()?.let { Facelets.faceOf(it) } ?: r.target
                rescanFace(f)
            }
            Ui.ReadFromStart -> startRead(false, force = true)
            is Ui.FixColor -> s.read?.let { r ->
                val i = r.fixing ?: return
                // a centre defines its face: it may not become another centre's colour (user addendum)
                if (i % 9 == 4 && (0 until 6).any { f -> f != i / 9 && readCube.centerColor(f) == e.color }) {
                    soundOrNull?.nudge()
                    set("centreClash") { it.copy(read = r.copy(centreClash = true)) }
                    return
                }
                readCube = readCube.with(i, e.color)
                edited += i
                set("fix") { it.copy(read = r.copy(fixing = null, recent = r.recent + i, centreClash = false, amber = r.amber - i, inferred = r.inferred - i)) }
                soundOrNull?.select()
                if (r.failure != null) checkRead() else if (r.mode == ReadMode.CONFIRM) reinfer()
            }
            Ui.ConfirmRead -> s.read?.let { r ->
                // 对，就是这样: the legality check, then the virtual cube (S08) or S07
                set("confirmRead") { it.copy(read = r.copy(amber = emptySet(), fixing = null)) }
                checkRead()
            }
            Ui.FixCancel -> s.read?.let { r -> set("fixCancel") { it.copy(read = r.copy(fixing = null, centreClash = false)) } }
            Ui.PaintThisFace -> s.read?.let { r ->
                ScanBridge.pause(Long.MAX_VALUE / 4)
                set("paintThisFace") { it.copy(read = r.copy(mode = ReadMode.PAINT, paintOnly = r.target, live = null, guard = null)) }
            }
            Ui.PaintAll -> s.read?.let { r ->
                ScanBridge.stop("paintAll")
                toPaintAll(r, camOk = r.camOk)
            }
            Ui.BackToScan -> s.read?.let { r ->
                if (r.issue == CamIssue.DENIED || !ScanBridge.running) beginScan(r.paintOnly?.let { null }) else {
                    ScanBridge.session?.restartClock(android.os.SystemClock.elapsedRealtime())
                    ScanBridge.resume()
                    set("backToScan") { it.copy(read = r.copy(mode = ReadMode.SCAN, paintOnly = null, tier = 1)) }
                }
            }
            Ui.AskCameraAgain -> s.read?.let { beginScan(null) }
            Ui.AmberOk -> {
                set("amberOk") { it.copy(readyAmber = emptySet()) }
                orientToHold(s.firstFront)
                save()
            }
            Ui.AmberFix -> {
                readCube = s.cube
                val first = s.readyAmber.minOrNull()?.let { Facelets.faceOf(it) } ?: Lessons.readFaces[0]
                val n = Lessons.readFaces.indexOf(first) + 1
                set("amberFix") { it.copy(ready = false, readyAmber = emptySet(), read = ReadState(face = n, target = first, mode = ReadMode.REVIEW, amber = s.readyAmber, scanned = (0 until 6).toSet(), camOk = false)) }
            }
            Ui.ReviewDone -> s.read?.let { r ->
                set("reviewDone") { it.copy(read = r.copy(amber = emptySet())) }
                checkRead()
            }
            Ui.StartReal -> beginLesson(LessonMode.REAL, s.firstFront)
            Ui.ReadyPlay -> set("readyPlay") { it.copy(ready = false, tab = Tab.PLAY, play = PlayState(level = it.play.level)) }
            Ui.ReplaceYes -> {
                val t = s.replaceAsk ?: return
                set("replace yes") { it.copy(replaceAsk = null, lesson = null, why = null) }
                when (t) {
                    ReplaceTarget.READ -> startRead(false)
                    ReplaceTarget.LEARN -> learnVirtual()
                    ReplaceTarget.SCRAMBLE -> scramble()
                }
            }
            Ui.ReplaceNo -> set("replace no") { it.copy(replaceAsk = null) }

            is Ui.TapHandle -> tapHandle(e.handle, e.src)
            is Ui.PushStick -> push(e.push, e.src)
            is Ui.Arrow -> {
                val sel = s.selection
                if (sel == null) {
                    hint(HintKind.NO_PICK)
                    soundOrNull?.nudge()
                } else {
                    val (a, b) = Handles.arrows(sel.row)
                    push(if (e.first) a else b, e.src)
                }
            }
            is Ui.ClearPick -> if (s.selection != null) set("clearPick ${e.src}") { it.copy(selection = null) }

            Ui.TurnedIt, Ui.HelpTurn -> helpTurn()
            Ui.ShowAgain -> showAgain()
            Ui.Undo -> undo()
            Ui.Why -> startWhy()
            Ui.WhyAgain -> s.why?.let { playWhy(it.stage) }
            Ui.WhyOk -> endWhy()
            Ui.NextStage -> s.lesson?.let { l -> if (l.stageDone) startStage(l.stage + 1, false) }
            Ui.NotSame -> startRead(true)
            Ui.Recompute -> recompute()
            Ui.AgainRound -> {
                set("again") { it.copy(tab = Tab.PLAY, lesson = null) }
                scramble()
            }
            Ui.ReadNext -> {
                set("readNext") { it.copy(lesson = null, tab = Tab.HOME) }
                startRead(false)
            }

            is Ui.Level -> set("level ${e.moves}") { it.copy(play = it.play.copy(level = e.moves)) }
            Ui.Scramble -> if (s.lessonLive) set("replace?") { it.copy(replaceAsk = ReplaceTarget.SCRAMBLE) } else scramble()
            Ui.ToLesson -> beginLesson(LessonMode.VIRTUAL, null)

            is Ui.SetPrefs -> {
                pref(e.why, e.f)
            }
            Ui.Recenter -> {
                set("recenter") { it.copy(ask = SettingsAsk.NONE) }
                WindowCtl.recenter("settings")
            }
            is Ui.Ask -> set("ask ${e.ask}") { it.copy(ask = e.ask) }
            Ui.ResetYes -> {
                store?.clearSave()
                anims.addLast(Anim.Snap(Cube.solved(), "reset"))
                set("reset") { it.copy(ask = SettingsAsk.NONE, lesson = null, why = null, read = null, ready = false, cube = Cube.solved(), hasCube = false, history = emptyList(), selection = null, play = PlayState(level = it.play.level)) }
            }
            Ui.ResetBestYes -> {
                pref("resetBest") { it.copy(bestMs = 0L, bestMoves = 0) }
                set("resetBest") { it.copy(ask = SettingsAsk.NONE) }
            }
            Ui.QuitYes -> quit()
        }
    }

    private fun pref(why: String, f: (Prefs) -> Prefs) {
        val p = f(_state.value.prefs)
        store?.savePrefs(p)
        soundOrNull?.setVolume(p.volume, p.muted)
        set("prefs $why") { it.copy(prefs = p) }
    }

    private fun hint(kind: HintKind) {
        val h = HintMsg(kind, now())
        set("hint $kind") { it.copy(hint = h) }
        main.postDelayed({ if (_state.value.hint == h) set("hint clear") { it.copy(hint = null) } }, if (kind == HintKind.SWITCHED) 1500L else 2000L)
    }

    private fun busy(): Boolean = animating || anims.isNotEmpty() || _state.value.lesson?.computing == true || _state.value.why != null

    // ============================================================================================== reading (S02–S08)

    /** The cube being read; shown instead of the virtual cube while reading. */
    var readCube: Cube = Cube.blank()
        private set

    /** Faces read so far (scanned with their colours' Lab, or painted), URFDLB → read. Kept across rescans. */
    private val faceReads = HashMap<Int, FaceRead>()
    private var scanTimer: Runnable? = null

    private fun startRead(reconcile: Boolean, force: Boolean = false) {
        val s = _state.value
        if (s.read != null && !force && !reconcile && s.read.reconcile == reconcile) {
            set("read continue") { it.copy(tab = Tab.HOME) }
            return
        }
        ScanBridge.stop("newRead")
        faceReads.clear()
        edited.clear()
        pendingInference = null
        readCube =
            if (reconcile) s.cube
            else Cube.solved().let { solved -> var c = Cube.blank(); for (f in 0 until 6) c = c.with(Facelets.center(f), solved.centerColor(f)); c }
        set(if (reconcile) "reconcile" else "read") { it.copy(tab = Tab.HOME, ready = false, readyAmber = emptySet(), read = ReadState(face = 1, target = Lessons.readFaces[0], reconcile = reconcile, paint = CubeColor.WHITE, mode = ReadMode.SCAN), selection = null) }
        beginScan(null)
    }

    /**
     * Starts the camera reader (S02) for every unread face, or just [only] (重扫这一面). Camera unavailable (emulator
     * without injected pictures, denied, debug camerafail) → S05 → S04 (G6 tier ③).
     */
    private fun beginScan(only: Int?) {
        val ctx = activity ?: return camIssue(CamIssue.FAILED, "no activity")
        val r0 = _state.value.read ?: return
        if (ScanBridge.forceDeny) return camIssue(CamIssue.DENIED, "debug camdeny")
        if (ScanBridge.forceFail) return camIssue(CamIssue.FAILED, "debug camerafail")
        val injected = ScanBridge.injector != null
        if (!injected && ScanBridge.isEmulator) return camIssue(CamIssue.FAILED, "emulator: no camera")
        if (!injected && !CameraPermission.granted(ctx)) {
            set("camPermission") { it.copy(read = r0.copy(mode = ReadMode.SCAN, issue = null, live = null)) }
            CameraPermission.request(ctx) { ok -> if (_state.value.read != null) if (ok) beginScan(only) else camIssue(CamIssue.DENIED, "denied") }
            return
        }
        val seed = HashMap(faceReads)
        if (only != null) seed.remove(only)
        val session = ScanSession(Lessons.readFaces, only, seed = seed)
        val target = only ?: session.expectedFace
        set("scan only=${only?.let { Facelets.FACES[it] }}") { it.copy(read = r0.copy(mode = ReadMode.SCAN, issue = null, camOk = true, failure = null, fixing = null, paintOnly = null, face = Lessons.readFaces.indexOf(target) + 1, target = target, live = null, progress = -1f, tier = 1, guard = null, scanned = faceReads.keys.toSet())) }
        val ok = ScanBridge.start(ctx, session, ::onScan) { why, msg -> camIssue(CamIssue.FAILED, "$why $msg") }
        if (!ok) camIssue(CamIssue.FAILED, "no source")
    }

    private fun onScan(e: ScanSession.Event) {
        val r = _state.value.read ?: return
        if (r.mode != ReadMode.SCAN) return
        when (e) {
            is ScanSession.Event.Live -> {
                val q = ScanBridge.session?.lastGrid?.let { GridFinder.quality(it, ScanBridge.frameW, ScanBridge.roi) }
                if (r.live == e.colors && r.tier == e.tier && r.guard == e.guard && r.quality == q && (r.progress - e.progress) * (r.progress - e.progress) < 0.01f) return
                if (e.tier != r.tier) CtLog.i("SCAN tier ${e.tier} face=${Facelets.FACES[r.target]}")
                _state.value = _state.value.copy(read = r.copy(live = e.colors, progress = e.progress, tier = e.tier, guard = e.guard, quality = q))
            }
            is ScanSession.Event.Confirmed -> {
                val fr = e.read
                faceReads[fr.face] = fr
                var c = readCube
                for (k in 0 until 9) c = c.with(fr.face * 9 + k, fr.colors[k])
                readCube = c
                soundOrNull?.scanGot()
                val amber = r.amber + fr.amber.map { fr.face * 9 + it }
                CtLog.i("SCAN got ${Facelets.FACES[fr.face]} ${fr.colors.joinToString("") { it.code.toString() }} amber=${fr.amber} guard=${e.guard} done=${e.done}")
                val inf = inferFromReads()
                CtLog.i("INFER after ${faceReads.size} faces: $inf")
                val (ask, cands) = when (inf) {
                    is Inference.Result.Unique -> null to 1
                    is Inference.Result.Ambiguous -> inf.askFace to inf.count
                    else -> null to 0
                }
                ScanBridge.session?.wanted = ask
                pendingInference = inf
                set("scanGot ${Facelets.FACES[fr.face]}") { it.copy(read = r.copy(target = fr.face, live = fr.colors, progress = 1f, gotAt = now(), guard = e.guard, scanned = faceReads.keys.toSet(), amber = amber, tier = 1, askFace = ask, candidates = cands)) }
                // S03 for 1 s: the card turns green, then the next face (reference sample: pause so faces do not cross-read)
                if (debugHoldGot) {
                    ScanBridge.pause(Long.MAX_VALUE / 4)
                    set("scanHold") { st -> st.copy(read = st.read?.copy(gotAt = Long.MAX_VALUE / 2)) }
                    return
                }
                ScanBridge.pause(1000L)
                scanTimer?.let { main.removeCallbacks(it) }
                val t = Runnable { afterGot(e.done) }
                scanTimer = t
                main.postDelayed(t, 1000L)
            }
        }
    }

    private fun afterGot(done: Boolean) {
        val r = _state.value.read ?: return
        if (r.mode != ReadMode.SCAN) return
        val s = ScanBridge.session
        val inf = pendingInference
        if (inf is Inference.Result.Unique || done || s == null || s.done || faceReads.size >= 6) {
            ScanBridge.stop(if (inf is Inference.Result.Unique) "inferred" else "all read")
            finishScan()
            return
        }
        s.restartClock(android.os.SystemClock.elapsedRealtime())
        set("scanNext ${Facelets.FACES[s.expectedFace]}") { it.copy(read = r.copy(face = s.step, target = s.expectedFace, live = null, progress = -1f, guard = null, tier = 1)) }
    }

    /** Six faces read (any mix of scanned and painted): grouping + U/D fix + the legality check (S08 / S07). */
    private fun finishScan() {
        val r = _state.value.read ?: return
        val inf = pendingInference ?: inferFromReads()
        pendingInference = null
        edited.clear()
        if (inf is Inference.Result.Unique && faceReads.size < 6) {
            // fewer than six faces and the rest is certain: fill in, mark as inferred, ask the user (user addendum)
            readCube = inf.cube
            val amber = faceReads.values.flatMap { fr -> fr.amber.map { fr.face * 9 + it } }.toSet()
            CtLog.i("SCAN inferred ${inf.inferred.size} squares from ${faceReads.size} faces cube=${inf.cube.code()}")
            set("inferred") { it.copy(read = r.copy(mode = ReadMode.CONFIRM, inferred = inf.inferred, amber = amber, live = null, target = 2, face = Lessons.readFaces.indexOf(2) + 1, askFace = null, candidates = 1)) }
            return
        }
        for (f in 0 until 6) if (!faceReads.containsKey(f)) faceReads[f] = FaceRead(f, (0 until 9).map { readCube[f * 9 + it] }, null, List(9) { 1f })
        val res = CubeAssembler.assemble(faceReads)
        CtLog.i("SCAN assembled legal=${res.legal} turnU=${res.turnU} turnD=${res.turnD} amber=${res.amber.map { Facelets.name(it) }} cube=${res.cube.code()}")
        readCube = res.cube
        // the user confirms before the virtual cube appears (user addendum 2026-11-11), legal or not
        set("assembled") { it.copy(read = r.copy(mode = ReadMode.CONFIRM, amber = res.amber, inferred = emptySet(), live = null, target = 2, face = Lessons.readFaces.indexOf(2) + 1, askFace = null)) }
    }

    private var pendingInference: Inference.Result? = null

    /** Squares the user changed in the confirm view: they count as read for the next inference. */
    private val edited = HashSet<Int>()

    /** Inference from the faces read so far; a scanned top / bottom held turned is tried in all 16 turns (unturned first). */
    private fun inferFromReads(extra: Map<Int, CubeColor> = emptyMap()): Inference.Result {
        val hasU = faceReads.containsKey(0)
        val hasD = faceReads.containsKey(3)
        var best: Inference.Result = Inference.Result.None
        for (tu in 0 until (if (hasU) 4 else 1)) for (td in 0 until (if (hasD) 4 else 1)) {
            val k = arrayOfNulls<CubeColor>(54)
            for ((f, fr) in faceReads) for (j in 0 until 9) {
                val t = if (f == 0) tu else if (f == 3) td else 0
                k[f * 9 + CubeAssembler.turnIndex(j, t)] = fr.colors[j]
            }
            for ((i, c) in extra) k[i] = c
            val r = Inference.infer(k)
            if (r is Inference.Result.Unique) return r
            if (best == Inference.Result.None && r != Inference.Result.None) best = r
        }
        return best
    }

    /** Confirm view: after an edit, the inferred squares are worked out again from what was read + edited. */
    private fun reinfer() {
        val r = _state.value.read ?: return
        if (r.inferred.isEmpty()) return
        val extra = edited.associateWith { readCube[it] }
        val inf = inferFromReads(extra)
        CtLog.i("INFER after edit ${edited.map { Facelets.name(it) }}: $inf")
        if (inf is Inference.Result.Unique) {
            readCube = inf.cube
            set("reinferred") { it.copy(read = r.copy(inferred = inf.inferred - edited, candidates = 1)) }
        } else set("reinfer $inf") { it.copy(read = r.copy(candidates = if (inf is Inference.Result.Ambiguous) inf.count else 0)) }
    }

    private fun camIssue(issue: CamIssue, why: String) {
        val r = _state.value.read ?: return
        CtLog.i("CAM unavailable issue=$issue why=$why")
        ScanBridge.stop("camIssue")
        soundOrNull?.nudge()
        set("camIssue $issue") { it.copy(read = r.copy(mode = ReadMode.UNAVAILABLE, issue = issue, camOk = false, live = null)) }
        // S05 → S04 after 2 s, faces already read stay (debug scanhold keeps S05 up for a screenshot)
        if (!debugHoldGot) main.postDelayed({ _state.value.read?.let { rr -> if (rr.mode == ReadMode.UNAVAILABLE) toPaintAll(rr, camOk = false) } }, 2000L)
    }

    /** S04 for every face still unread, in the read order. */
    private fun toPaintAll(r: ReadState, camOk: Boolean) {
        val next = Lessons.readFaces.firstOrNull { it !in faceReads.keys } ?: r.target
        set("paintAll") { it.copy(read = r.copy(mode = ReadMode.PAINT, paintOnly = null, camOk = camOk, face = Lessons.readFaces.indexOf(next) + 1, target = next, live = null, guard = null)) }
    }

    /** S07 重扫这一面 / S06 [重扫这一面]: read just that face again (camera if it can, else paint it). */
    private fun rescanFace(f: Int) {
        val r = _state.value.read ?: return
        faceReads.remove(f)
        val n = Lessons.readFaces.indexOf(f) + 1
        if (!r.camOk && r.issue != null) {
            set("repaint ${Facelets.FACES[f]}") { it.copy(read = r.copy(mode = ReadMode.PAINT, paintOnly = null, face = n, target = f, failure = null, fixing = null)) }
            return
        }
        set("rescan ${Facelets.FACES[f]}") { it.copy(read = r.copy(face = n, target = f, failure = null, fixing = null, amber = r.amber.filter { it / 9 != f }.toSet())) }
        beginScan(f)
    }

    private fun tapSticker(i: Int) {
        val s = _state.value
        val r = s.read ?: return CtLog.i("TAP sticker ${Facelets.name(i)} ignored (not reading)")
        if (s.tab != Tab.HOME) return
        if (r.failure != null || r.mode == ReadMode.REVIEW || r.mode == ReadMode.CONFIRM) {
            // S07 / S08 [去改] -> S06: tap a sticker to change it
            set("fixAsk ${Facelets.name(i)}") { it.copy(read = r.copy(fixing = i)) }
            return
        }
        if (r.mode != ReadMode.PAINT && !r.reconcile) return CtLog.i("TAP sticker ${Facelets.name(i)} ignored (mode ${r.mode})")
        readCube = readCube.with(i, r.paint)
        soundOrNull?.select()
        set("paint ${Facelets.name(i)}=${r.paint}") { it.copy(read = r.copy(recent = r.recent + i)) }
    }

    fun faceFilled(face: Int): Int = (0 until 9).count { readCube[face * 9 + it] != CubeColor.NONE }

    private fun faceDone() {
        val r = _state.value.read ?: return
        if (faceFilled(r.target) < 9) return
        soundOrNull?.scanGot()
        faceReads[r.target] = FaceRead(r.target, (0 until 9).map { readCube[r.target * 9 + it] }, null, List(9) { 1f })
        if (r.paintOnly != null) {
            // this face was painted (tier ③); back to the camera for the rest
            val s = ScanBridge.session
            s?.painted(r.target, faceReads.getValue(r.target).colors, android.os.SystemClock.elapsedRealtime())
            val inf = inferFromReads()
            if (inf is Inference.Result.Unique) pendingInference = inf
            s?.wanted = (inf as? Inference.Result.Ambiguous)?.askFace
            if (s == null || s.done || inf is Inference.Result.Unique) {
                ScanBridge.stop("painted last")
                finishScan()
                return
            }
            ScanBridge.resume()
            set("painted ${Facelets.FACES[r.target]}") { it.copy(read = r.copy(mode = ReadMode.SCAN, paintOnly = null, face = s.step, target = s.expectedFace, scanned = faceReads.keys.toSet(), live = null, tier = 1)) }
            return
        }
        val inf = inferFromReads()
        if (inf is Inference.Result.Unique) {
            pendingInference = inf
            finishScan()
            return
        }
        val next = (inf as? Inference.Result.Ambiguous)?.askFace ?: Lessons.readFaces.firstOrNull { it !in faceReads.keys }
        if (next == null) finishScan()
        else set("face ${Facelets.FACES[next]}") { it.copy(read = r.copy(face = Lessons.readFaces.indexOf(next) + 1, target = next, scanned = faceReads.keys.toSet())) }
    }

    private fun checkRead() {
        val r = _state.value.read ?: return
        val f = Legality.check(readCube, r.recent)
        if (f != null) {
            CtLog.i("CHECK fail ${f.kind} suspects=${f.suspects.map { Facelets.name(it) }} counts=${f.counts}")
            soundOrNull?.nudge()
            val sf = f.suspects.firstOrNull()?.let { Facelets.faceOf(it) } ?: r.target
            set("check fail") { it.copy(read = r.copy(failure = f, fixing = null, target = sf, face = Lessons.readFaces.indexOf(sf) + 1)) }
            return
        }
        CtLog.i("CHECK ok cube=${readCube.code()}")
        ScanBridge.stop("checked")
        val cube = readCube
        if (r.reconcile) {
            // S12: re-plan from the cube you really hold
            anims.addLast(Anim.Snap(cube, "reconcile"))
            set("reconciled") { it.copy(read = null, cube = cube, tab = Tab.LEARN, history = emptyList()) }
            save()
            recompute()
            return
        }
        // S08: the read cube becomes the virtual cube; unsure squares are asked about first, then the tutorial hold
        val front = cube.centerColor(2)
        val amber = r.amber.filter { cube[it] != CubeColor.NONE }.toSet()
        anims.addLast(Anim.Snap(cube, "read"))
        set("ready") { it.copy(read = null, ready = true, readyAmber = amber, cube = cube, hasCube = true, history = emptyList(), firstFront = front, lesson = null, play = PlayState(level = it.play.level)) }
        if (amber.isEmpty()) orientToHold(front)
        save()
    }

    // ============================================================================================== cube turns

    /** Applies a turn to the logical cube, queues its animation and records it for 悔一步. */
    private fun applyTurn(t: Turn, record: Boolean, lessonIdx: Int?, half: Turn?, ms: Long = TURN_MS) {
        val s = _state.value
        val cube = s.cube.turn(t)
        anims.addLast(Anim.Spin(t, ms, t.whole))
        set("turn ${Moves.nameOf(t) ?: t}") { it.copy(cube = cube, history = if (record) (it.history + Done(t, lessonIdx, half)).takeLast(300) else it.history) }
    }

    private fun orientToHold(front: CubeColor?) {
        val tokens = Solver.orient(_state.value.cube, front) ?: return
        for (tok in tokens) for (q in Moves.token(tok)) applyTurn(q, record = false, lessonIdx = null, half = null)
        if (tokens.isNotEmpty()) CtLog.i("HOLD orient ${tokens.joinToString(" ")}")
    }

    private fun tapHandle(h: Handle, src: String) {
        val s = _state.value
        if (s.read != null && s.tab == Tab.HOME) return
        if (s.why != null) return
        val r = Handles.tap(s.selection, h)
        soundOrNull?.select()
        set("pick ${h.id} -> ${r.selection} src=$src") { it.copy(selection = r.selection) }
        if (r.switchedRow) hint(HintKind.SWITCHED)
    }

    private fun push(p: Push, src: String) {
        val s = _state.value
        val sel = s.selection
        if (sel == null) {
            hint(HintKind.NO_PICK)
            soundOrNull?.nudge()
            return
        }
        val t = Handles.turnFor(sel, p)
        if (t == null) {
            hint(if (sel.row == Row.ROW) HintKind.ROW_LR else HintKind.COL_UD)
            return
        }
        userTurn(t, src)
    }

    /** A turn the user made (handles + stick / arrow, debug `turn=`): checked against the lesson, counted in free play. */
    fun userTurn(t: Turn, src: String) {
        val s = _state.value
        if (s.read != null && s.tab == Tab.HOME) return
        if (busy()) {
            CtLog.i("TURN ignored busy src=$src")
            hint(HintKind.BUSY)
            return
        }
        val l = s.lesson
        if (l != null && !l.solved && !l.stageDone && !l.computing && l.offScript == null) {
            val step = l.step
            if (step != null) {
                val q = Moves.token(step.move)
                val want = l.halfTurn ?: q[0]
                val firstOfHalf = q.size == 2 && l.halfTurn == null && (t == q[0] || t == q[0].inverse)
                val ok = if (l.halfTurn != null) t == l.halfTurn else (t == q[0] || firstOfHalf)
                applyTurn(t, record = true, lessonIdx = l.idx, half = l.halfTurn)
                soundFor(t)
                if (ok && q.size == 2 && l.halfTurn == null) {
                    setLesson("half ${step.move}") { it.copy(halfTurn = t, undoMove = null) }
                    hint(HintKind.HALF)
                } else if (ok) {
                    advance("turn $src")
                } else {
                    CtLog.i("OFFSCRIPT did=${Moves.nameOf(t) ?: t} want=${step.move} (${want})")
                    setLesson("offscript") { it.copy(offScript = OffScript(Moves.nameOf(t), step.move), undoMove = null) }
                    soundOrNull?.nudge()
                }
                countPlay()
                save()
                return
            }
        }
        applyTurn(t, record = true, lessonIdx = null, half = null)
        soundFor(t)
        countPlay()
        save()
    }

    private fun soundFor(t: Turn) {
        if (t.whole) soundOrNull?.whole() else soundOrNull?.turn()
    }

    private fun countPlay() {
        val s = _state.value
        val p = s.play
        if (!p.scrambled || p.solved || s.tab != Tab.PLAY) return
        val start = if (p.startMs == 0L) now() else p.startMs
        val moves = p.moves + 1
        if (s.cube.isSolved) {
            val ms = now() - start
            val best = s.prefs.bestMs == 0L || ms < s.prefs.bestMs
            if (best) pref("best") { it.copy(bestMs = ms, bestMoves = moves) }
            set("play solved") { it.copy(play = p.copy(startMs = start, endMs = now(), moves = moves, solved = true, newBest = best), selection = null) }
            soundOrNull?.solved()
            CtLog.i("PLAY solved moves=$moves ms=$ms best=$best")
        } else {
            set("play move") { it.copy(play = p.copy(startMs = start, moves = moves)) }
        }
    }

    // ============================================================================================== lesson

    private fun setLesson(why: String, f: (LessonState) -> LessonState) {
        set("lesson $why") { s -> s.lesson?.let { s.copy(lesson = f(it)) } ?: s }
    }

    private fun learnVirtual() {
        // design S01 [跟着学]: a full scramble, straight into the lesson (S10)
        val seed = SystemClock.elapsedRealtime()
        val c = Cube.solved().apply(Scrambler.make(25, seed).joinToString(" "))
        anims.addLast(Anim.Snap(c, "scramble25"))
        set("learnVirtual") { it.copy(cube = c, hasCube = true, history = emptyList(), read = null, ready = false, play = PlayState(level = it.play.level)) }
        CtLog.i("SCRAMBLE 25 seed=$seed")
        beginLesson(LessonMode.VIRTUAL, null)
    }

    fun beginLesson(mode: LessonMode, front: CubeColor?) {
        orientToHold(front)
        set("lesson begin $mode") { it.copy(tab = Tab.LEARN, ready = false, lesson = LessonState(mode, 1, front = front, startMs = now()), history = emptyList(), why = null, replaceAsk = null) }
        startStage(Solver.firstOpenStage(_state.value.cube), recomputed = false)
        save()
    }

    /** Plans stage [k] (and skips the stages already done) on a worker thread (≤ 0.4 s on the JVM, design limit 50 ms is per-stage median). */
    private fun startStage(k: Int, recomputed: Boolean) {
        val cube = _state.value.cube
        setLesson("plan L$k") { it.copy(computing = true, stageDone = false, offScript = null, idx = 0, halfTurn = null, steps = emptyList(), undoMove = null, recomputed = recomputed, stage = k.coerceAtMost(7)) }
        stageJob?.cancel()
        stageJob =
            scope.launch {
                val t0 = SystemClock.elapsedRealtime()
                val (stage, steps, skipped) =
                    withContext(Dispatchers.Default) {
                        var kk = k
                        val sk = ArrayList<Int>()
                        var st: List<Solver.Step> = emptyList()
                        while (kk <= 7) {
                            st = runCatching { Solver.stage(cube, kk) }.getOrElse { e -> CtLog.w("PLAN L$kk failed: $e"); emptyList() }
                            if (st.isNotEmpty()) break
                            sk += kk
                            kk++
                        }
                        Triple(kk, st, sk)
                    }
                val ms = SystemClock.elapsedRealtime() - t0
                CtLog.i("PLAN L$stage steps=${steps.size} skipped=$skipped ms=$ms moves=${steps.joinToString(" ") { it.move }}")
                if (_state.value.cube != cube) CtLog.w("PLAN cube changed while planning")
                if (stage > 7 || steps.isEmpty()) {
                    setLesson("solved") { it.copy(computing = false, solved = true, stage = 7, skipped = skipped) }
                    soundOrNull?.solved()
                } else {
                    setLesson("planned L$stage") { it.copy(computing = false, stage = stage, steps = steps, idx = 0, skipped = skipped.filter { s -> s >= k }) }
                }
                if (recomputed) main.postDelayed({ setLesson("recomputed off") { it.copy(recomputed = false) } }, 2500L)
            }
    }

    private fun advance(why: String) {
        val l = _state.value.lesson ?: return
        val next = l.idx + 1
        if (next >= l.steps.size) {
            val solved = l.stage >= 7 || _state.value.cube.isSolved
            setLesson("stage end L${l.stage} $why") { it.copy(idx = next, halfTurn = null, correctAt = now(), stageDone = !solved, solved = solved, turns = it.turns + 1, undoMove = null) }
            if (solved) soundOrNull?.solved() else soundOrNull?.stageDone()
            CtLog.i("STAGE done L${l.stage} solved=$solved")
        } else {
            setLesson("step ${next + 1}/${l.steps.size} $why") { it.copy(idx = next, halfTurn = null, correctAt = now(), turns = it.turns + 1, undoMove = null) }
            soundOrNull?.correct()
        }
        main.postDelayed({ set("tick") { it } }, 650L)
    }

    /** 转好了 (real cube) / 帮我转 (virtual): the virtual cube makes this step (the rest of a half turn) and the lesson goes on. */
    private fun helpTurn() {
        val l = _state.value.lesson ?: return
        if (busy() || l.stageDone || l.solved || l.offScript != null) return
        val step = l.step ?: return
        val q = Moves.token(step.move)
        val todo = if (l.halfTurn != null) listOf(l.halfTurn) else q
        for (t in todo) applyTurn(t, record = true, lessonIdx = l.idx, half = l.halfTurn, ms = if (todo.size == 2) HALF_MS / 2 else TURN_MS)
        soundFor(q[0])
        advance("help")
        save()
    }

    /** 再演示一遍: the virtual cube makes this step and turns it back; the lesson does not move on. */
    private fun showAgain() {
        val l = _state.value.lesson ?: return
        if (busy()) return
        val step = l.step ?: return
        val q = Moves.token(step.move)
        for (t in q) anims.addLast(Anim.Spin(t, TURN_MS * 2, t.whole))
        for (t in q.reversed()) anims.addLast(Anim.Spin(t.inverse, TURN_MS * 2, t.whole))
        CtLog.i("DEMO again ${step.move}")
    }

    private fun undo() {
        val s = _state.value
        if (busy()) return
        val last = s.history.lastOrNull() ?: return
        val cube = s.cube.turn(last.turn.inverse)
        anims.addLast(Anim.Spin(last.turn.inverse, TURN_MS, last.turn.whole))
        soundFor(last.turn)
        set("undo ${Moves.nameOf(last.turn)}") { st ->
            val l = st.lesson
            val lesson =
                if (l != null && last.lessonIdx != null && !l.solved) {
                    val back = l.copy(idx = last.lessonIdx, halfTurn = last.lessonHalf, offScript = null, stageDone = false, correctAt = 0L, undoMove = if (l.mode == LessonMode.REAL) Moves.nameOf(last.turn.inverse) else null)
                    // a step taken back from the next stage's start keeps the stage
                    back
                } else l?.copy(offScript = null)
            st.copy(cube = cube, history = st.history.dropLast(1), lesson = lesson, play = if (st.play.scrambled && !st.play.solved && st.play.moves > 0) st.play.copy(moves = st.play.moves - 1) else st.play)
        }
        save()
    }

    /** S27 「就这样，重新算」 / S12: re-plan from the cube as it is (turned back to the hold first if a whole turn moved white). */
    private fun recompute() {
        val l = _state.value.lesson ?: return
        orientToHold(l.front)
        set("recompute") { it.copy(history = emptyList(), tab = Tab.LEARN) }
        startStage(Solver.firstOpenStage(_state.value.cube), recomputed = true)
    }

    // ---- 看为什么 (S13): the stage's teaching position, played slowly; the real cube comes back after

    private fun startWhy() {
        val l = _state.value.lesson ?: return
        if (animating || anims.isNotEmpty()) return
        set("why L${l.stage}") { it.copy(why = WhyState(l.stage), selection = null) }
        playWhy(l.stage)
    }

    private fun playWhy(stage: Int) {
        whyJob?.cancel()
        val demo = Lessons.info(stage).demo
        val start = Lessons.demoStart(stage)
        anims.clear()
        anims.addLast(Anim.Snap(start, "why L$stage"))
        set("why play") { it.copy(why = WhyState(stage, -1)) }
        whyJob =
            scope.launch {
                delay(900)
                for ((i, pair) in demo.withIndex()) {
                    if (_state.value.why == null) return@launch
                    set("why step ${i + 1}") { it.copy(why = it.why?.copy(step = i)) }
                    for (t in Moves.token(pair.first)) anims.addLast(Anim.Spin(t, DEMO_STEP_MS / 2, t.whole))
                    delay(DEMO_STEP_MS)
                }
                set("why done") { it.copy(why = it.why?.copy(done = true)) }
            }
    }

    private fun endWhy() {
        whyJob?.cancel()
        anims.clear()
        anims.addLast(Anim.Snap(_state.value.cube, "why end"))
        set("why ok") { it.copy(why = null) }
    }

    // ============================================================================================== free play

    private fun scramble() {
        val s = _state.value
        val seed = SystemClock.elapsedRealtime()
        scrambleWith(s.play.level, seed)
    }

    fun scrambleWith(level: Int, seed: Long) {
        val moves = Scrambler.make(level, seed)
        val c = Cube.solved().apply(moves.joinToString(" "))
        anims.addLast(Anim.Snap(c, "scramble$level"))
        set("scramble $level seed=$seed") { it.copy(cube = c, hasCube = true, history = emptyList(), lesson = null, why = null, tab = Tab.PLAY, read = null, ready = false, play = PlayState(level = level, scrambled = true), replaceAsk = null) }
        CtLog.i("SCRAMBLE $level seed=$seed ${moves.joinToString(" ")}")
        save()
    }

    // ============================================================================================== save / quit

    private fun save() {
        main.removeCallbacks(saver)
        main.postDelayed(saver, 400L)
    }

    private val saver =
        Runnable {
            val s = _state.value
            store?.save(Store.Saved(s.cube.code(), s.hasCube, s.lesson?.takeIf { !it.solved }?.mode?.name, s.play.level))
        }

    fun onBackground(bg: Boolean) {
        if (bg) {
            // the camera is released in the background and asked again when you come back (D41)
            ScanBridge.stop("background")
            main.removeCallbacks(saver)
            saver.run()
            soundOrNull?.release()
        } else {
            soundOrNull?.start()
            soundOrNull?.setVolume(_state.value.prefs.volume, _state.value.prefs.muted)
            _state.value.read?.let { r -> if (r.mode == ReadMode.SCAN && r.failure == null && !ScanBridge.running) beginScan(ScanBridge.session?.only) }
        }
    }

    /** D26 exit: release, close the window, close the Stage, remove the tasks, end (factory template order). */
    private fun quit() {
        CtLog.i("EXIT confirmed: release + close window + closeStage + finishAndRemoveTask + exit")
        saver.run()
        ScanBridge.stop("quit")
        soundOrNull?.release()
        soundOrNull = null
        quitHook?.invoke()
        WindowCtl.closeForQuit()
        val nav = WindowCtl.navigator
        val a = activity
        MainScope().launch {
            runCatching { nav?.closeStage() }.onFailure { CtLog.w("EXIT closeStage: $it") }
            runCatching {
                val am = a?.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                am?.appTasks?.forEach { runCatching { it.finishAndRemoveTask() } }
                a?.finishAndRemoveTask()
            }.onFailure { CtLog.w("EXIT finishAndRemoveTask: $it") }
            main.postDelayed({ exitProcess(0) }, 400)
        }
    }

    // ============================================================================================== debug seams (src/debug drives them)

    /** Debug `loadamber=N`: a legal read cube with N unsure squares → S08 asks first (design readFlow.review). */
    fun debugLoadAmber(code: String, n: Int) {
        val c = Cube.parse(code) ?: return
        ScanBridge.stop("loadamber")
        readCube = c
        val amber = (0 until 54).filter { it % 9 != 4 }.shuffled(java.util.Random(3)).take(n).toSet()
        set("loadamber") { it.copy(tab = Tab.HOME, ready = false, readyAmber = emptySet(), read = ReadState(face = 6, target = 3, mode = ReadMode.SCAN, scanned = (0 until 6).toSet(), amber = amber)) }
        checkRead()
    }

    fun debugLoad(code: String): String {
        val c = Cube.parse(code) ?: return "bad cube code"
        // a bad cube goes through the reading check exactly like a read one (S07)
        readCube = c
        ScanBridge.stop("load")
        faceReads.clear()
        set("load") { it.copy(tab = Tab.HOME, ready = false, readyAmber = emptySet(), read = ReadState(face = 6, target = 3, mode = ReadMode.PAINT, recent = listOf(Facelets.F + 1), scanned = (0 until 6).toSet())) }
        checkRead()
        return "state=${_state.value.designState}"
    }

    /** Debug: S03 stays up (screenshots) instead of moving on after 1 s. */
    var debugHoldGot = false

    /** Debug: restart the reader on whatever source is configured now (injected pictures / camera). */
    fun debugRescan() {
        val r = _state.value.read ?: return
        if (r.failure != null || r.mode == ReadMode.REVIEW) return
        beginScan(null)
    }

    /** Debug `tier=2|3`: the G6 prompts without waiting 4 s / 30 s (moves the session clock back). */
    fun debugTier(t: Int) {
        val s = ScanBridge.session ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        s.restartClock(now - if (t >= 3) ScanSession.TIER3_MS + 100 else if (t == 2) ScanSession.TIER2_MS + 100 else 0)
    }

    fun debugReadFill(face: Int, cells: Int) {
        val r = _state.value.read ?: return
        val f = Lessons.readFaces[face - 1]
        val solved = Cube.solved()
        var c = readCube
        var n = 0
        for (k in 0 until 9) {
            val i = f * 9 + k
            if (k == 4) continue
            if (n >= cells) break
            c = c.with(i, solved[i])
            n++
        }
        readCube = c
        // filling by hand is S04: the camera is not used for this face
        if (r.mode == ReadMode.SCAN) ScanBridge.pause(Long.MAX_VALUE / 4)
        set("readFill") { it.copy(read = r.copy(face = face, target = f, mode = ReadMode.PAINT, paintOnly = if (r.mode == ReadMode.SCAN) f else r.paintOnly, live = null)) }
    }

    fun debugCoach(on: Boolean) {
        if (!on) pref("coach_reset") { it.copy(coachSeen = false) }
        set("coach=$on") { it.copy(coach = on || !it.prefs.coachSeen, tab = Tab.HOME) }
    }

    fun debugResetAll() {
        store?.clearAll()
        readCube = Cube.blank()
        anims.addLast(Anim.Snap(Cube.solved(), "reset"))
        set("debug reset") { CtUiState(loading = false, prefs = Prefs(), coach = true, windowOpen = it.windowOpen, panelEpoch = it.panelEpoch) }
    }

    /** Debug `lesson=Lk step=n mode=…`: a virtual cube at stage k, n steps into it (built by playing the plan, same path). */
    fun debugLesson(k: Int, step: Int, mode: LessonMode, at: String? = null) {
        whyJob?.cancel()
        // a scramble whose solve reaches stage k: play the tutor's own plan up to stage k on a fixed scramble
        var c = Cube.solved().apply(Scrambler.make(25, 7L).joinToString(" "))
        for (kk in 1 until k) c = c.apply(Solver.stage(c, kk).joinToString(" ") { it.move })
        anims.clear()
        anims.addLast(Anim.Snap(c, "debug lesson"))
        set("debug lesson L$k") { it.copy(cube = c, hasCube = true, history = emptyList(), read = null, ready = false, why = null, tab = Tab.LEARN, lesson = LessonState(mode, k, startMs = now()), selection = null) }
        val steps = Solver.stage(c, k)
        setLesson("debug plan") { it.copy(computing = false, steps = steps, idx = 0) }
        val step = if (at != null) (steps.indexOfFirst { it.move == at } + 1).coerceAtLeast(1) else step
        // walk n-1 steps with the lesson's own 帮我转 path, without animation waits
        repeat((step - 1).coerceAtLeast(0).coerceAtMost(steps.size - 1)) {
            val l = _state.value.lesson ?: return
            val q = Moves.token(l.step!!.move)
            var cube = _state.value.cube
            for (t in q) cube = cube.turn(t)
            set("debug step") { s -> s.copy(cube = cube, lesson = l.copy(idx = l.idx + 1)) }
        }
        anims.clear()
        anims.addLast(Anim.Snap(_state.value.cube, "debug lesson step"))
        save()
    }

    fun debugStageDone() {
        val l = _state.value.lesson ?: return
        val s = _state.value
        var cube = s.cube
        for (i in l.idx until l.steps.size) cube = cube.apply(l.steps[i].move)
        anims.clear()
        anims.addLast(Anim.Snap(cube, "debug done"))
        val solved = l.stage >= 7 || cube.isSolved
        set("debug stageDone") { it.copy(cube = cube, lesson = l.copy(idx = l.steps.size, stageDone = !solved, solved = solved)) }
        if (solved) soundOrNull?.solved() else soundOrNull?.stageDone()
    }

    fun debugSolvedPlay() {
        // free play: the last move of a 1-move scramble, made through the same turn path
        scrambleWith(1, 3L)
        val s = _state.value
        val code = Scrambler.make(1, 3L).first()
        set("debug solvedPlay") { it.copy(tab = Tab.PLAY) }
        for (t in Moves.token(Moves.inverse(code))) {
            animating = false
            userTurnNoLock(t, "debug")
        }
        CtLog.i("debug solved: cube solved=${_state.value.cube.isSolved} state=${s.designState}")
    }

    /** The debug path for a turn while an animation is still queued (the emulator runs at ~1 fps). */
    fun userTurnNoLock(t: Turn, src: String) {
        val saved = ArrayDeque(anims)
        anims.clear()
        val wasAnim = animating
        animating = false
        userTurn(t, src)
        val mine = ArrayDeque(anims)
        anims.clear()
        anims.addAll(saved)
        anims.addAll(mine)
        animating = wasAnim
    }

    fun debugWhy(step: Int) {
        val l = _state.value.lesson ?: return
        whyJob?.cancel()
        val start = Lessons.demoStart(l.stage)
        var c = start
        val demo = Lessons.info(l.stage).demo
        for (i in 0 until step.coerceAtMost(demo.size)) c = c.apply(demo[i].first)
        anims.clear()
        anims.addLast(Anim.Snap(c, "debug why"))
        set("debug why") { it.copy(why = WhyState(l.stage, (step - 1).coerceAtLeast(0))) }
    }
}
