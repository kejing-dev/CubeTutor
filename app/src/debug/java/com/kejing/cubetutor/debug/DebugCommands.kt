package com.kejing.cubetutor.debug

import android.content.Context
import com.kejing.cubetutor.domain.CubeColor
import com.kejing.cubetutor.domain.Moves
import com.kejing.cubetutor.domain.Push
import com.kejing.cubetutor.platform.CtLog
import com.kejing.cubetutor.ui.CtController
import com.kejing.cubetutor.ui.Lang
import com.kejing.cubetutor.ui.LessonMode
import com.kejing.cubetutor.ui.SettingsAsk
import com.kejing.cubetutor.ui.Tab
import com.kejing.cubetutor.ui.Ui
import com.kejing.cubetutor.ui.ScanBridge
import com.kejing.cubetutor.platform.camera.InjectedFrameSource
import com.kejing.cubetutor.ui.components.TextFit
import com.kejing.cubetutor.ui.debug.Probe
import com.kejing.cubetutor.ui.input.CubeNames
import com.kejing.cubetutor.ui.window.PANEL_ID
import com.kejing.cubetutor.ui.window.PANEL_TAG
import com.kejing.cubetutor.ui.window.WindowCtl

/**
 * Debug builds only (design verification.debugHooks). A command is `key=value` pairs run left to right:
 *
 *   adb -s emulator-5554 shell "am broadcast -a com.kejing.cubetutor.DEBUG -p com.kejing.cubetutor --es cmd 'lesson=L2 step=2 mode=virtual'"
 *
 * Every pair sends the window's own events ([CtController.onEvent]), taps the scene's own targets (the same path as a
 * pinch, a controller ray or a stick push) or plays a lesson with its own steps: no command writes an app state directly
 * (factory rule 2026-10-29). Each state change logs `STATE Sxx…`. List and screenshot recipes: AGENTS.md.
 */
object DebugCommands {
    private fun ev(e: Ui) = CtController.onEvent(e)

    fun run(context: Context, raw: String): String {
        val pairs = raw.trim().split(Regex("\\s+")).map { it.substringBefore('=') to it.substringAfter('=', "1") }
        val args = pairs.toMap()
        val scene = Probe.host?.scene()
        for ((k, v) in pairs) {
            when (k) {
                "tab" -> ev(Ui.TabTo(when (v) { "learn" -> Tab.LEARN; "play" -> Tab.PLAY; "settings" -> Tab.SETTINGS; else -> Tab.HOME }))
                "state" -> goState(v)
                "read" -> ev(Ui.ReadMine)
                "face" -> ev(Ui.GoFace(v.toInt()))
                "fill" -> CtController.state.value.read?.let { r -> CtController.debugReadFill(r.face, v.toInt()) }
                "fillall" -> CtController.state.value.read?.let { r -> for (f in r.face..6) { CtController.debugReadFill(f, 8); if (f < 6) ev(Ui.FaceDone) } }
                "paint" -> ev(Ui.Paint(CubeColor.values().first { it.name.equals(v, true) || it.code.toString().equals(v, true) }))
                "facedone" -> ev(Ui.FaceDone)
                "readagain" -> ev(Ui.ReadFaceAgain)
                "fromstart" -> ev(Ui.ReadFromStart)
                "fix" -> ev(Ui.FixColor(CubeColor.values().first { it.name.equals(v, true) || it.code.toString().equals(v, true) }))
                "load" -> CtController.debugLoad(v)
                "ready" -> { ev(Ui.ReadMine); CtController.debugLoad(com.kejing.cubetutor.domain.Cube.solved().apply(com.kejing.cubetutor.domain.Scrambler.make(20, (args["seed"] ?: "5").toLong()).joinToString(" ")).code()) }
                "scramble" -> CtController.scrambleWith(v.toInt(), (args["seed"] ?: "7").toLong())
                "level" -> ev(Ui.Level(v.toInt()))
                "tap" -> tap(v)
                "push" -> ev(Ui.PushStick(Push.valueOf(v.uppercase()), "debug"))
                "clear" -> ev(Ui.ClearPick("debug"))
                "turn" -> for (q in Moves.token(v)) CtController.userTurnNoLock(q, "debug")
                "lesson" -> CtController.debugLesson(v.removePrefix("L").toInt(), (args["step"] ?: "1").toInt(), if (args["mode"] == "real") LessonMode.REAL else LessonMode.VIRTUAL, args["at"])
                "bad" -> { ev(Ui.ReadMine); CtController.debugLoad(badCube(v)) }
                "do", "help" -> ev(Ui.HelpTurn)
                "turned" -> ev(Ui.TurnedIt)
                "again" -> ev(Ui.ShowAgain)
                "undo" -> ev(Ui.Undo)
                "done" -> CtController.debugStageDone()
                "why" -> ev(Ui.Why)
                "demoStep" -> CtController.debugWhy(v.toInt())
                "whyok" -> ev(Ui.WhyOk)
                "next" -> ev(Ui.NextStage)
                "reconcile", "notsame" -> ev(Ui.NotSame)
                "recompute" -> ev(Ui.Recompute)
                "solved" -> CtController.debugSolvedPlay()
                "replace" -> ev(if (v == "no") Ui.ReplaceNo else Ui.ReplaceYes)
                "closepanel" -> runCatching { WindowCtl.navigator?.closeWindowContainer(PANEL_ID, PANEL_TAG) }
                "openpanel" -> WindowCtl.reopen("debug")
                "recall", "recenter" -> ev(Ui.Recenter)
                "drag" -> scene?.debugDrag(v, (args["dx"] ?: "0").toFloat(), (args["dy"] ?: "0").toFloat(), (args["dz"] ?: "0").toFloat())
                "reset" -> CtController.debugResetAll()
                "coach" -> CtController.debugCoach(true)
                "coach_reset" -> CtController.debugCoach(false)
                "coachok" -> ev(Ui.CoachOk)
                "lang" -> ev(Ui.SetPrefs("lang") { it.copy(lang = when (v) { "zh" -> Lang.ZH; "en" -> Lang.EN; else -> Lang.SYSTEM }) })
                "mute" -> ev(Ui.SetPrefs("mute") { it.copy(muted = v == "1") })
                "volume" -> ev(Ui.SetPrefs("volume") { it.copy(volume = v.toInt()) })
                "freeze" -> scene?.frozen = v != "0"
                "view" -> scene?.debugYawDeg = v.toFloat()
                "quit" -> when (v) { "yes" -> ev(Ui.QuitYes); "cancel" -> ev(Ui.Ask(SettingsAsk.NONE)); else -> { ev(Ui.TabTo(Tab.SETTINGS)); ev(Ui.Ask(SettingsAsk.EXIT)) } }
                "resetask" -> ev(Ui.Ask(SettingsAsk.RESET))
                "learnvirtual" -> ev(Ui.LearnVirtual)
                "playvirtual" -> ev(Ui.PlayVirtual)
                "startreal" -> ev(Ui.StartReal)
                "tolesson" -> ev(Ui.ToLesson)
                "rescramble" -> ev(Ui.Scramble)
                "fit" -> { if (v == "reset") TextFit.reset(); CtLog.i(TextFit.summary()) }
                "where" -> CtLog.i("WHERE ${scene?.where()}")
                "instances" -> scene?.logInstances()
                // v1.6 scan (design emulatorVerification.inject): pictures instead of the camera, same reader code
                "scanimg" -> inject(context, listOf(v), args)
                "scanset" -> inject(context, (1..6).map { "$v/face$it.png" }, args)
                "scanfake" -> { ScanBridge.fake = if (v == "0") null else v; if (ScanBridge.injector == null) inject(context, listOf("scan/setA/face1.png"), args) }
                "scanoff" -> { ScanBridge.injector = null; ScanBridge.fake = null; ScanBridge.holdStable = false; ScanBridge.forceFail = false; ScanBridge.forceDeny = false; CtController.debugHoldGot = false }
                "scanhold" -> CtController.debugHoldGot = v != "0"
                "scanpause" -> ScanBridge.holdStable = v != "0"
                "loadamber" -> { ev(Ui.ReadMine); CtController.debugLoadAmber(com.kejing.cubetutor.domain.Cube.solved().apply(com.kejing.cubetutor.domain.Scrambler.make(20, (args["seed"] ?: "5").toLong()).joinToString(" ")).code(), v.toInt()) }
                "camerafail" -> ScanBridge.forceFail = v != "0"
                "camdeny" -> ScanBridge.forceDeny = v != "0"
                "dumpframe" -> CtLog.i("DUMP ${ScanBridge.dump(context, args["tag"] ?: "f")}")
                "paintface" -> ev(Ui.PaintThisFace)
                "paintall" -> ev(Ui.PaintAll)
                "backscan" -> ev(Ui.BackToScan)
                "askcam" -> ev(Ui.AskCameraAgain)
                "amberok" -> ev(Ui.AmberOk)
                "amberfix" -> ev(Ui.AmberFix)
                "reviewdone" -> ev(Ui.ReviewDone)
                "tier" -> CtController.debugTier(v.toInt())
                "blank" -> CtLog.i(blank())
                "roi" -> { ScanBridge.roi = if (v == "full") com.kejing.cubetutor.domain.scan.GridFinder.Roi.FULL else if (v == "default") com.kejing.cubetutor.domain.scan.GridFinder.Roi.DEFAULT else v.split(",").map { it.toFloat() }.let { com.kejing.cubetutor.domain.scan.GridFinder.Roi(it[0], it[1], it[2], it[3]) }; CtLog.i("SCAN roi=${ScanBridge.roi}") }
                "confirm" -> ev(Ui.ConfirmRead)
                "fixcancel" -> ev(Ui.FixCancel)
                "step", "mode", "seed", "dx", "dy", "dz", "at", "hold", "noise", "tag" -> Unit // arguments of the commands above
                else -> CtLog.w("CMD unknown '$k'")
            }
        }
        return "state=${CtController.state.value.designState}"
    }

    /**
     * D44 blank measure on the window (profile: content block = first to last visible control; blank = top + bottom
     * outside it ÷ window height; inner bands = largest vertical gap between controls ÷ window height).
     */
    private fun blank(): String {
        val (w, h) = Probe.sizeOf("window") ?: return "BLANK no window"
        val boxes = Probe.controlsOf("window").values.filter { it.w > 0f && it.h > 0f && it.cy - it.h / 2 >= 0f && it.cy + it.h / 2 <= h + 1 }
        if (boxes.isEmpty()) return "BLANK no controls"
        val iv = boxes.map { (it.cy - it.h / 2) to (it.cy + it.h / 2) }.sortedBy { it.first }
        val top = iv.first().first
        val bottom = iv.maxOf { it.second }
        var reach = iv.first().second
        var gap = 0f
        for ((a, b) in iv.drop(1)) {
            if (a > reach) gap = maxOf(gap, a - reach)
            reach = maxOf(reach, b)
        }
        val st = CtController.state.value
        return String.format(java.util.Locale.US, "BLANK state=%s lang=%s window=%dx%d top=%.0f bottom=%.0f outside=%.1f%% innerGap=%.1f%% controls=%d", st.designState, st.prefs.lang, w, h, top, bottom, 100f * (top + (h - bottom)) / h, 100f * gap / h, boxes.size)
    }

    /** Injected pictures replace the camera; a read already running restarts on them (same path as 读我的魔方). */
    private fun inject(context: Context, pics: List<String>, args: Map<String, String>) {
        val noise = (args["noise"] ?: "0").toFloat()
        val hold = ((args["hold"] ?: "0").toFloat() * 1000).toLong()
        ScanBridge.injector = { ctx -> InjectedFrameSource(ctx, pics, noise, hold) }
        CtLog.i("SCAN inject ${pics.size} picture(s) noise=$noise hold=$hold")
        CtController.debugRescan()
    }

    /** Three illegal cubes for S07 (design acceptance: 数色 / 块 / 奇偶 each one screenshot), from a fixed scramble. */
    private fun badCube(kind: String): String {
        val c = com.kejing.cubetutor.domain.Cube.solved().apply(com.kejing.cubetutor.domain.Scrambler.make(20, 11L).joinToString(" "))
        val L = com.kejing.cubetutor.domain.Legality
        return when (kind) {
            "count" -> { val i = com.kejing.cubetutor.domain.Facelets.F + 1; c.with(i, CubeColor.SIX.first { it != c[i] }).code() }
            "piece" -> { val a = L.cornerFacelets[0][1]; val b = L.cornerFacelets[1][1]; var x = c; for (s in listOf(a to b)) { val ca = x[s.first]; val cb = x[s.second]; x = if (ca != cb) x.with(s.first, cb).with(s.second, ca) else x }; if (L.check(x)?.kind == com.kejing.cubetutor.domain.Legality.Kind.PIECE) x.code() else c.with(L.cornerFacelets[0][0], c[L.cornerFacelets[0][1]]).with(L.cornerFacelets[0][1], c[L.cornerFacelets[0][0]]).code() }
            else -> { val e = L.edgeFacelets[1]; c.with(e[0], c[e[1]]).with(e[1], c[e[0]]).code() }
        }
    }

    private fun tap(v: String) {
        val scene = Probe.host?.scene() ?: return
        val name =
            when {
                v.startsWith("sticker:") -> "st:" + v.removePrefix("sticker:")
                v.startsWith("handle:") -> "h:" + v.removePrefix("handle:")
                v == "dpad:a" || v == "dpad:left" || v == "dpad:up" -> "dp:a"
                v == "dpad:b" || v == "dpad:right" || v == "dpad:down" -> "dp:b"
                else -> v
            }
        val t = CubeNames.resolveName(name) ?: return CtLog.w("CMD tap: unknown target '$v'")
        scene.debugTarget(t)
    }

    /** Reaches a named design state through the user's own events. */
    private fun goState(s: String) {
        when (s) {
            "S01" -> { ev(Ui.TabTo(Tab.HOME)) }
            "S02" -> ev(Ui.ReadMine)
            "S04" -> { ScanBridge.forceDeny = false; ScanBridge.forceFail = true; ev(Ui.ReadMine) }
            "S05" -> { ScanBridge.forceFail = true; ev(Ui.ReadMine) }
            "S16" -> ev(Ui.TabTo(Tab.PLAY))
            "S18" -> { CtController.debugResetAll(); ev(Ui.TabTo(Tab.LEARN)) }
            "S20" -> ev(Ui.TabTo(Tab.SETTINGS))
            "S21" -> { ev(Ui.TabTo(Tab.SETTINGS)); ev(Ui.Ask(SettingsAsk.EXIT)) }
            else -> CtLog.w("CMD state=$s: use the recipe in AGENTS.md")
        }
    }
}
