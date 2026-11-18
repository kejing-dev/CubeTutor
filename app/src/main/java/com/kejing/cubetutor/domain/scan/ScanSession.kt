package com.kejing.cubetutor.domain.scan

import com.kejing.cubetutor.domain.Cube
import com.kejing.cubetutor.domain.CubeColor

/** One face as read: URFDLB face index, nine colours row by row, their Lab (null when painted by hand) and confidence. */
data class FaceRead(val face: Int, val colors: List<CubeColor>, val labs: List<Lab>?, val conf: List<Float>) {
    val amber: List<Int>
        get() = conf.indices.filter { conf[it] < ColorModel.AMBER }
}

/** Why a stable face was not taken, or taken with a note (design readFlow.guards). */
enum class Guard { REPEAT, REVERSED, WRONG_SIDE }

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
    private var x0 = 0f
    private var y0 = 0f

    /** [x], [y], [pitch]: where the face's centre is (a face that moves more than [MAX_MOVE] pitches starts over). */
    fun feed(now: Long, c: List<CubeColor>?, l: List<Lab>?, x: Float = 0f, y: Float = 0f, pitch: Float = 1f): Float {
        if (c == null || l == null) {
            if (colors != null && ++misses <= 1) return progress(now)
            reset()
            return -1f
        }
        misses = 0
        val moved = colors != null && (x - x0) * (x - x0) + (y - y0) * (y - y0) > (MAX_MOVE * pitch) * (MAX_MOVE * pitch)
        if (c != colors || moved) {
            reset()
            colors = c
            since = now
            x0 = x
            y0 = y
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
        /** User 2026-11-17: 0.6 s took faces while still turning; held still for 0.9 s, square-on, not moving. */
        const val STABLE_MS = 900L
        const val MIN_FRAMES = 4
        const val MAX_MOVE = 0.35f
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

    /** 重扫这一面 at the per-face confirm: the face is unread again and asked for next. */
    fun forget(face: Int, now: Long) {
        reads.remove(face)
        wanted = face
        advance(now)
    }

    /** The user confirmed (and maybe fixed) a face: keep their colours, learn the colours from all nine squares. */
    fun confirmed(read: FaceRead) {
        reads[read.face] = read
        read.labs?.let { model.learn(read.colors, it) }
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
        // User 2026-11-17: the face being read is the one we asked for ("show the <colour>-centre side"), so its
        // centre colour is known — never trust the camera's centre colour. The measured centre is that colour in this
        // light: the other eight squares are read against it.
        val face = expectedFace
        val centre = Cube.SCHEME.getValue(face)
        val seen = grid?.cells?.map { it.lab }
        // only a centre that could be this colour teaches it (red / orange look alike on the headset; a colour whose
        // side is already read cannot be a new centre, so the camera got it wrong). A centre clearly of another unread
        // side means that side is shown: read it plainly and do not take it.
        val plain = seen?.let { model.classify(it[4]).first }
        val plausible = plain != null && (plain == centre || plain == partner(centre) || Inference.faceOf(plain)?.let { it in reads } == true)
        if (plausible && lastGuard == Guard.WRONG_SIDE) lastGuard = null
        val cls = seen?.let { l -> val m = if (plausible) model.withCentreAs(l[4], centre) else model; l.map { m.classify(it) } }
        val shown = cls?.map { it.first }?.toMutableList()?.also { it[4] = centre }
        // a face still turning (not square to the camera) is shown but never taken (user 2026-11-17)
        val square = grid != null && GridFinder.squareOn(grid)
        val colors = if (square) shown else null
        val labs = if (square) seen else null
        val p = stab.feed(now, colors, labs, grid?.cells?.get(4)?.x ?: 0f, grid?.cells?.get(4)?.y ?: 0f, grid?.pitch ?: 1f)
        if (colors == null || !stab.stable(now) || holdAtStable) return Event.Live(shown, p, tier(now), lastGuard)
        val mean = stab.meanLabs()
        // the same side shown again: its nine measured colours match a side already read (names are not compared:
        // they depend on which centre they were read against)
        if (reads.values.any { r -> r.face != face && r.labs != null && (0 until 9).sumOf { k -> mean[k].dist(r.labs[k]).toDouble() } / 9 < REPEAT_DIST }) {
            lastGuard = Guard.REPEAT
            return Event.Live(colors, p, tier(now), Guard.REPEAT)
        }
        if (!plausible) {
            lastGuard = Guard.WRONG_SIDE
            return Event.Live(colors, p, tier(now), Guard.WRONG_SIDE)
        }
        val fm = model.withCentreAs(mean[4], centre)
        val final = mean.map { fm.classify(it) }
        val got = final.map { it.first }.toMutableList().also { it[4] = centre }
        val read = FaceRead(face, got, mean, final.map { it.second }.toMutableList().also { it[4] = 1f })
        reads[face] = read
        lastGuard = null
        advance(now)
        return Event.Confirmed(read, null, done)
    }

    /**
     * The user changed the centre at the per-face confirm (they showed another side than the one asked for): the read
     * moves to that face. False when that face is already read.
     */
    fun moveTo(from: Int, to: Int, read: FaceRead): Boolean {
        if (to != from && reads.containsKey(to)) return false
        reads.remove(from)
        reads[to] = read.copy(face = to)
        return true
    }

    private fun partner(c: CubeColor): CubeColor =
        when (c) {
            CubeColor.RED -> CubeColor.ORANGE
            CubeColor.ORANGE -> CubeColor.RED
            else -> c
        }

    private fun advance(now: Long) {
        stab.reset()
        stepStart = now
        step = (reads.size + 1).coerceAtMost(6)
    }

    companion object {
        const val TIER2_MS = 4000L
        const val TIER3_MS = 30000L

        /** Mean Lab distance (L weighted) of the nine squares below which a side is the same side shown again. */
        const val REPEAT_DIST = 10.0
    }
}
