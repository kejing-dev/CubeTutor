package com.kejing.cubetutor.domain.scan

import com.kejing.cubetutor.domain.CubeColor

/** One face as read: URFDLB face index, nine colours row by row, their Lab (null when painted by hand) and confidence. */
data class FaceRead(val face: Int, val colors: List<CubeColor>, val labs: List<Lab>?, val conf: List<Float>) {
    val amber: List<Int>
        get() = conf.indices.filter { conf[it] < ColorModel.AMBER }
}

/** Why a stable face was not taken, or taken with a note (design readFlow.guards). */
enum class Guard { REPEAT, REVERSED }

/**
 * Stability (design readFlow.confirm; reference ScanManager: same 9 patch values for 1 s at 200 ms samples, then a
 * majority vote): a face is taken when the nine colours stayed the same for [STABLE_MS] over ≥ [MIN_FRAMES] frames.
 * One frame without a grid (blink, motion blur) is forgiven; a different colour anywhere restarts the clock, so a
 * shaking hand does not confirm a half-read face. The Lab values are averaged over the stable run.
 */
class Stabilizer {
    private var colors: List<CubeColor>? = null
    private var since = 0L
    private var frames = 0
    private var misses = 0
    private val labs = ArrayList<List<Lab>>()

    fun reset() {
        colors = null
        frames = 0
        misses = 0
        labs.clear()
    }

    /** Progress 0..1 towards confirmation after this frame, or −1 when nothing is being tracked. */
    fun feed(now: Long, c: List<CubeColor>?, l: List<Lab>?): Float {
        if (c == null || l == null) {
            if (colors != null && ++misses <= 1) return progress(now)
            reset()
            return -1f
        }
        misses = 0
        if (c != colors) {
            reset()
            colors = c
            since = now
        }
        frames++
        labs += l
        if (labs.size > 12) labs.removeAt(0)
        return progress(now)
    }

    private fun progress(now: Long): Float = if (colors == null) -1f else ((now - since).toFloat() / STABLE_MS).coerceIn(0f, 1f)

    fun stable(now: Long): Boolean = colors != null && frames >= MIN_FRAMES && now - since >= STABLE_MS

    fun meanLabs(): List<Lab> = (0 until 9).map { k -> Lab.mean(labs.map { it[k] }) }

    companion object {
        const val STABLE_MS = 600L
        const val MIN_FRAMES = 3
    }
}

/**
 * Reading the six faces in the order of cubeSpec.readOrder (design readFlow), with the G6 fallback tiers:
 * ① found and confirmed automatically; ② after [TIER2_MS] without a confirmed face, a big prompt to hold the face
 * squarely towards the headset camera; ③ after [TIER3_MS] the prompt to paint this face yourself (S04). Camera
 * failures (no frames, denied) are the controller's (S05 → S04).
 *
 * [only] = read just that URFDLB face (重扫这一面). Pure: time comes in with every call.
 */
class ScanSession(private val readFaces: IntArray, val only: Int? = null, val model: ColorModel = ColorModel(), seed: Map<Int, FaceRead> = emptyMap()) {
    sealed class Event {
        /** A frame was looked at: live colours (null = no grid), stability progress (−1 = none), tier 1..3, guard. */
        data class Live(val colors: List<CubeColor>?, val progress: Float, val tier: Int, val guard: Guard?, val grid: Grid? = null) : Event()

        /** A face was taken (S03). [guard] = REVERSED when it was read the other way round (still fine). */
        data class Confirmed(val read: FaceRead, val guard: Guard?, val done: Boolean) : Event()
    }

    /** 1..6: the step of the read order. */
    var step = 1
        private set

    /** Turned right instead of left at step 2: steps 2 and 4 swap R / L. */
    var reversed = false
        private set

    val reads = LinkedHashMap<Int, FaceRead>()

    /** The grid of the last frame (the frame UI shows far / tilted from it). */
    var lastGrid: Grid? = null
        private set

    /** Debug (screenshots of the gold "hold still" frame): stay at "stable" without taking the face. */
    var holdAtStable = false
    private val stab = Stabilizer()
    private var stepStart = -1L
    private var lastGuard: Guard? = null

    init {
        // faces read before (scanned or painted) stay read; their centres teach the colour model
        for ((f, r) in seed) if (f != only) {
            reads[f] = r
            r.labs?.let { model.calibrate(r.colors[4], it[4]) }
        }
        step = (reads.size + 1).coerceAtMost(6)
    }

    /** The face asked for next (inference names it; otherwise the next unread one in the read order). */
    var wanted: Int? = null

    val expectedFace: Int
        get() = only ?: wanted?.takeIf { it !in reads } ?: readFaces.firstOrNull { it !in reads } ?: readFaces[5]

    val done: Boolean
        get() = if (only != null) reads.containsKey(only) else reads.size >= 6

    fun tier(now: Long): Int {
        if (stepStart < 0) return 1
        val t = now - stepStart
        return when {
            t >= TIER3_MS -> 3
            t >= TIER2_MS -> 2
            else -> 1
        }
    }

    /** Restarts the step clock (a new face, or the user came back from 「这面点颜色」). */
    fun restartClock(now: Long) {
        stepStart = now
        stab.reset()
    }

    /** A face was painted by hand (S04): counts as read. */
    fun painted(face: Int, colors: List<CubeColor>, now: Long) {
        reads[face] = FaceRead(face, colors, null, List(9) { 1f })
        advance(now)
    }

    fun onGrid(grid: Grid?, now: Long): Event {
        if (stepStart < 0) stepStart = now
        if (done) return Event.Live(null, -1f, 1, null)
        lastGrid = grid
        val labs = grid?.cells?.map { it.lab }
        val cls = labs?.map { model.classify(it) }
        val colors = cls?.map { it.first }
        val p = stab.feed(now, colors, labs)
        if (colors == null || !stab.stable(now) || holdAtStable) return Event.Live(colors, p, tier(now), lastGuard)
        val center = colors[4]
        // the centre colour says which face this is (user addendum: centres never move, standard scheme)
        val face = Inference.faceOf(center) ?: return Event.Live(colors, p, tier(now), lastGuard)
        if (reads.containsKey(face) || (only != null && face != only)) {
            lastGuard = Guard.REPEAT
            return Event.Live(colors, p, tier(now), Guard.REPEAT)
        }
        val mean = stab.meanLabs()
        val final = mean.map { model.classify(it) }
        val read = FaceRead(face, final.map { it.first }.toMutableList().also { it[4] = center }, mean, final.map { it.second })
        model.calibrate(center, mean[4])
        reads[face] = read
        lastGuard = null
        advance(now)
        return Event.Confirmed(read, null, done)
    }

    private fun advance(now: Long) {
        stab.reset()
        stepStart = now
        step = (reads.size + 1).coerceAtMost(6)
    }

    companion object {
        const val TIER2_MS = 4000L
        const val TIER3_MS = 30000L
    }
}
