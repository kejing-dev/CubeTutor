package com.kejing.cubetutor.ui

import com.kejing.cubetutor.domain.Cube
import com.kejing.cubetutor.domain.CubeColor
import com.kejing.cubetutor.domain.Handle
import com.kejing.cubetutor.domain.Legality
import com.kejing.cubetutor.domain.Moves
import com.kejing.cubetutor.domain.Push
import com.kejing.cubetutor.domain.Selection
import com.kejing.cubetutor.domain.Solver
import com.kejing.cubetutor.domain.Turn
import com.kejing.cubetutor.domain.scan.Guard

enum class Lang { SYSTEM, ZH, EN }

/** The main window's TabBar pages (design panels P1). */
enum class Tab { HOME, LEARN, PLAY, SETTINGS }

enum class LessonMode { REAL, VIRTUAL }

/** Persisted settings (design dataModel.prefs). */
data class Prefs(
    val volume: Int = 80,
    val muted: Boolean = false,
    val lang: Lang = Lang.SYSTEM,
    val coachSeen: Boolean = false,
    val bestMs: Long = 0L,
    val bestMoves: Int = 0,
)

/** How the cube is being read (design v1.6 readFlow + G6 tiers). */
enum class ReadMode {
    /** S02 / S03: the camera reads the face held towards it. */
    SCAN,

    /** S04: tap the colours yourself (one face, or every face still unread). */
    PAINT,

    /** S05: the camera cannot be used (denied / no frames / errors / emulator without pictures); → S04 after 2 s. */
    UNAVAILABLE,

    /**
     * S03 (user 2026-11-17): a face was taken and the camera waits. The nine colours are shown; tap a wrong square to
     * change it (S06), then 对，下一面 (the colours teach the colour model) or 重扫这一面.
     */
    FACE_OK,

    /** S06 entry from S08 [去改]: tap an amber (or any) square on the cube to change it. */
    REVIEW,

    /**
     * User addendum 2026-11-11: before the virtual cube appears, the user confirms the whole read cube (net in the
     * window + the loosened cube); inferred squares are marked, unsure ones amber; tap any square to change it (S06).
     */
    CONFIRM,
}

enum class CamIssue { DENIED, FAILED }

/**
 * Reading a real cube (S02–S07), also the reconcile mode after a stage (S14 「不一样」). [face] is the step of the read
 * order (1..6), [target] the URFDLB face being read now (they differ when the cube was turned the other way).
 */
data class ReadState(
    val face: Int = 1,
    val target: Int = 2,
    val paint: CubeColor = CubeColor.WHITE,
    val reconcile: Boolean = false,
    val failure: Legality.Failure? = null,
    val fixing: Int? = null,
    val recent: List<Int> = emptyList(),
    val mode: ReadMode = ReadMode.SCAN,
    /** Live colours of the face in front of the camera (null: no grid), progress to "taken" (−1: none). */
    val live: List<CubeColor>? = null,
    val progress: Float = -1f,
    /** G6 tier: 1 auto, 2 「正对摄像头」, 3 「自己涂色吧」. */
    val tier: Int = 1,
    val guard: Guard? = null,
    /** When the last face was taken (S03 lasts 1 s). */
    val gotAt: Long = 0L,
    /** URFDLB faces already read (scanned or painted). */
    val scanned: Set<Int> = emptySet(),
    /** Unsure squares (amber) of the faces read so far. */
    val amber: Set<Int> = emptySet(),
    /** S04 for just this face, then back to scanning. */
    val paintOnly: Int? = null,
    val issue: CamIssue? = null,
    /** The camera can be used (S04 offers [回去扫]). */
    val camOk: Boolean = true,
    /** Squares filled in by inference (not read): marked in the confirm view. */
    val inferred: Set<Int> = emptySet(),
    /** Inference after the last face: the face it asks for next, and how many candidate cubes are left (0 = none fits). */
    val askFace: Int? = null,
    val candidates: Int = -1,
    /** Where the grid sits in the alignment frame (far / tilted / ok; null = no grid). */
    val quality: com.kejing.cubetutor.domain.scan.GridFinder.Quality? = null,
    /** A centre may not be changed into another centre's colour (one-line hint while true). */
    val centreClash: Boolean = false,
)

/** What the off-script turn was and what the lesson wanted (S27). */
data class OffScript(val did: String?, val want: String)

/** The lesson (S09 / S10 / S11 / S12 / S14 / S15). */
data class LessonState(
    val mode: LessonMode,
    val stage: Int,
    val steps: List<Solver.Step> = emptyList(),
    val idx: Int = 0,
    /** The first quarter of a half-turn step, when only that one is done (either direction counts). */
    val halfTurn: Turn? = null,
    val computing: Boolean = true,
    val stageDone: Boolean = false,
    val solved: Boolean = false,
    val offScript: OffScript? = null,
    /** Real mode after 悔一步: the move to turn your cube back (「手里那只也拧回去」). */
    val undoMove: String? = null,
    /** S12: the lesson was re-planned from the cube as it is now. */
    val recomputed: Boolean = false,
    /** Stages found already done when this one started (「白十字已经好了，跳到下一段」). */
    val skipped: List<Int> = emptyList(),
    val front: CubeColor? = null,
    /** S11: the last step was right (green tick, 0.4 s). */
    val correctAt: Long = 0L,
    val startMs: Long = 0L,
    val turns: Int = 0,
) {
    val step: Solver.Step?
        get() = steps.getOrNull(idx)
}

/** "看为什么" (S13): the stage's fixed teaching position played slowly with captions. */
data class WhyState(val stage: Int, val step: Int = -1, val done: Boolean = false)

/** Free play (S16): level, the clock (starts at the first turn), moves, solved card. */
data class PlayState(
    val level: Int = 15,
    val scrambled: Boolean = false,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val moves: Int = 0,
    val solved: Boolean = false,
    val newBest: Boolean = false,
)

/** A one-line hint on the window / the bar (S24 switched row, S26 no pick, wrong push direction). */
enum class HintKind { SWITCHED, NO_PICK, ROW_LR, COL_UD, BUSY, HALF }

data class HintMsg(val kind: HintKind, val at: Long)

/** Where a confirmed "replace the cube" goes (S19). */
enum class ReplaceTarget { READ, LEARN, SCRAMBLE }

enum class SettingsAsk { NONE, EXIT, RESET, RESET_BEST }

/** A turn in the undo history: the turn and the lesson position before it. */
data class Done(val turn: Turn, val lessonIdx: Int?, val lessonHalf: Turn?)

data class CtUiState(
    val loading: Boolean = true,
    val tab: Tab = Tab.HOME,
    val cube: Cube = Cube.solved(),
    val hasCube: Boolean = false,
    val history: List<Done> = emptyList(),
    val selection: Selection? = null,
    val hint: HintMsg? = null,
    val read: ReadState? = null,
    val ready: Boolean = false,
    /** S08: unsure squares to ask about (「有 2 格我不太确定」) before the cube is turned to the tutorial hold. */
    val readyAmber: Set<Int> = emptySet(),
    val firstFront: CubeColor? = null,
    val lesson: LessonState? = null,
    val why: WhyState? = null,
    val play: PlayState = PlayState(),
    val replaceAsk: ReplaceTarget? = null,
    val ask: SettingsAsk = SettingsAsk.NONE,
    val prefs: Prefs = Prefs(),
    val coach: Boolean = false,
    val away: Boolean = false,
    val dragging: Boolean = false,
    val panelEpoch: Int = 0,
    val windowOpen: Boolean = false,
    val levelWait: Boolean = false,
    val recentering: Boolean = false,
) {
    val lessonLive: Boolean
        get() = lesson != null && !lesson.solved

    /** The handle + push the lesson wants now (pulsing gold), or null. */
    val lessonCue: Pair<Handle, Push>?
        get() {
            val l = lesson ?: return null
            if (why != null || l.stageDone || l.solved || l.computing || l.offScript != null) return null
            val s = l.step ?: return null
            val q = Moves.token(s.move)
            return com.kejing.cubetutor.domain.Handles.handleFor(q[0])
        }

    /** Design state id(s) for the STATE log and QA (design states S00–S30). */
    val designState: String
        get() {
            val base =
                when {
                    loading -> "S00"
                    recentering -> "S30"
                    !windowOpen && levelWait -> "S28"
                    tab == Tab.HOME -> when {
                        replaceAsk != null -> "S19"
                        read?.fixing != null -> "S06"
                        read?.failure != null -> "S07"
                        read?.mode == ReadMode.REVIEW -> "S06"
                        read?.mode == ReadMode.CONFIRM -> "S08"
                        read?.mode == ReadMode.FACE_OK -> "S03"
                        read?.mode == ReadMode.UNAVAILABLE -> "S05"
                        read?.mode == ReadMode.PAINT -> "S04"
                        read != null -> if (System.currentTimeMillis() - read.gotAt < 1000L) "S03" else "S02"
                        ready -> "S08"
                        else -> "S01"
                    }
                    tab == Tab.LEARN -> when {
                        replaceAsk != null -> "S19"
                        lesson == null -> "S18"
                        why != null -> "S13"
                        lesson.solved -> "S15"
                        lesson.offScript != null -> "S27"
                        lesson.stageDone -> "S14"
                        lesson.recomputed -> "S12"
                        lesson.correctAt > 0 && System.currentTimeMillis() - lesson.correctAt < 600 -> "S11"
                        lesson.mode == LessonMode.REAL -> "S09"
                        else -> "S10"
                    }
                    tab == Tab.PLAY -> when {
                        replaceAsk != null -> "S19"
                        play.solved -> "S15"
                        hint?.kind == HintKind.SWITCHED -> "S24"
                        hint?.kind == HintKind.NO_PICK -> "S26"
                        selection != null && selection.layers.size > 1 -> "S23"
                        selection != null -> "S17"
                        else -> "S16"
                    }
                    else -> if (ask == SettingsAsk.EXIT) "S21" else "S20"
                }
            val extra = buildList {
                if (!loading && !windowOpen && !levelWait && !recentering) add("S22")
                if (dragging) add("S29")
                if (tab == Tab.LEARN && selection != null && base in listOf("S09", "S10")) add(if (selection.layers.size > 1) "S23" else "S17")
            }
            return (listOf(base) + extra).joinToString("+")
        }
}
