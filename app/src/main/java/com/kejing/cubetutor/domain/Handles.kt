package com.kejing.cubetutor.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Single-controller layer select (design input.handles / multiSelect / joystick / dpad, profile D50 补): three rows of
 * handles beside the cube, each row picks layers along one display axis; push the stick (or tap an arrow) to turn the
 * picked layers a quarter. Everything is in the DISPLAY frame (the cube state is too, [Cube]), so after a whole-cube
 * turn "Right" still means the column on your right. Pure Kotlin.
 */
enum class Row(val axis: Axis) {
    /** Horizontal slices, handles left of the front face: Top / Mid / Bottom; they turn left / right. */
    ROW(Axis.Y),

    /** Vertical slices, handles under the front face: Left / Mid / Right; they turn up / down. */
    COL(Axis.X),

    /** Slices parallel to your face, handles in a column right of the cube: Front / Mid / Back; clockwise / counter. */
    DEPTH(Axis.Z),
}

enum class Push { UP, DOWN, LEFT, RIGHT }

/** One handle: a row and a slot (layer -1 / 0 / 1 along the row's axis, or [ALL] = the whole cube). */
data class Handle(val row: Row, val slot: Int) {
    val id: String
        get() = "${row.name.lowercase()}${if (slot == ALL) "All" else if (slot > 0) "+1" else if (slot < 0) "-1" else "0"}"

    val layers: Set<Int>
        get() = if (slot == ALL) setOf(-1, 0, 1) else setOf(slot)

    companion object {
        const val ALL = 9

        val every: List<Handle> = Row.values().flatMap { r -> listOf(1, 0, -1, ALL).map { Handle(r, it) } }

        /** "row+1", "col0", "depth-1", "rowAll" (debug `tap=handle:…`, entity names `h:<id>`). */
        fun parse(s: String): Handle? = every.firstOrNull { it.id.equals(s, ignoreCase = true) }
    }
}

/** What is picked: one row and 1–3 of its layers. */
data class Selection(val row: Row, val layers: Set<Int>) {
    val whole: Boolean
        get() = layers.size == 3
}

/** Result of a handle tap (design S17 / S23 / S24 / S16). */
data class TapResult(val selection: Selection?, val switchedRow: Boolean)

object Handles {
    /**
     * A tap on [h] with [current] picked: same row toggles that layer (再点一次取消), "whole" picks all three (or clears
     * when all three were picked), another row replaces the pick (提示「换成竖列了」).
     */
    fun tap(current: Selection?, h: Handle): TapResult {
        if (current == null || current.row != h.row) {
            return TapResult(Selection(h.row, h.layers), switchedRow = current != null)
        }
        val next =
            if (h.slot == Handle.ALL) (if (current.whole) emptySet() else setOf(-1, 0, 1))
            else if (h.slot in current.layers) current.layers - h.slot
            else current.layers + h.slot
        return TapResult(if (next.isEmpty()) null else Selection(h.row, next), false)
    }

    /**
     * The turn a push makes on [sel] (design spatialLayout.joystickMapping): row left = like U, right = U'; column up =
     * like R, down = R'; depth right / down = clockwise like F, left / up = counter-clockwise. Null = this push does
     * nothing for this row (the bar says 「横行往左或往右推」).
     */
    fun turnFor(sel: Selection, push: Push): Turn? =
        when (sel.row) {
            Row.ROW -> when (push) {
                Push.LEFT -> Turn(Axis.Y, sel.layers, -1)
                Push.RIGHT -> Turn(Axis.Y, sel.layers, 1)
                else -> null
            }
            Row.COL -> when (push) {
                Push.UP -> Turn(Axis.X, sel.layers, -1)
                Push.DOWN -> Turn(Axis.X, sel.layers, 1)
                else -> null
            }
            Row.DEPTH -> when (push) {
                Push.RIGHT, Push.DOWN -> Turn(Axis.Z, sel.layers, -1)
                Push.LEFT, Push.UP -> Turn(Axis.Z, sel.layers, 1)
            }
        }

    /** The two arrows the bar shows for a row: (first, second) pushes, left-to-right on the bar. */
    fun arrows(row: Row): Pair<Push, Push> =
        when (row) {
            Row.ROW -> Push.LEFT to Push.RIGHT
            Row.COL -> Push.UP to Push.DOWN
            Row.DEPTH -> Push.LEFT to Push.RIGHT
        }

    /**
     * The handle and push that make a quarter [turn] (lesson highlight, design input.lesson: R = 列「右」往上, D = 行「下」
     * 往右, y = 行「整只」往左). Null for a turn that is not one handle (two non-adjacent layers etc. never happen in the
     * lesson).
     */
    fun handleFor(turn: Turn): Pair<Handle, Push>? {
        val slot = if (turn.whole) Handle.ALL else turn.layers.singleOrNull() ?: return null
        return when (turn.axis) {
            Axis.Y -> Handle(Row.ROW, slot) to (if (turn.dir < 0) Push.LEFT else Push.RIGHT)
            Axis.X -> Handle(Row.COL, slot) to (if (turn.dir < 0) Push.UP else Push.DOWN)
            Axis.Z -> Handle(Row.DEPTH, slot) to (if (turn.dir < 0) Push.RIGHT else Push.LEFT)
        }
    }
}

/**
 * Stick pushes with a dead band (design input.joystick, D18, qubic PadInput): a push fires once when the stick passes
 * [fire] (0.6) and the next one only after it came back under [rearm] (0.3). Fed every frame.
 */
class PushDetector(val fire: Float = 0.6f, val rearm: Float = 0.3f) {
    private var armed = true

    /** Stick x (+ right) / y (+ up, the SDK's thumbstick convention); returns a push or null. */
    fun step(x: Float, y: Float): Push? {
        val m = hypot(x, y)
        if (!armed) {
            if (m < rearm) armed = true
            return null
        }
        if (m < fire) return null
        armed = false
        return if (abs(x) >= abs(y)) (if (x > 0) Push.RIGHT else Push.LEFT) else (if (y > 0) Push.UP else Push.DOWN)
    }

    fun reset() {
        armed = true
    }
}

/**
 * Where the cube rig goes (design spatialLayout.rigPlacement, M8, D9 / D14 / D20 / D41): at the moment the window
 * opens, the head's horizontal heading only; rig centre 43° to the left, 0.60 m away horizontally, 0.15 m below the
 * eyes, turned (yaw only) to face the head. The system opens the window straight ahead at ~1.75 m.
 */
object RigLayout {
    const val LEFT_DEG = 43f
    const val DIST_M = 0.60f
    const val BELOW_M = 0.15f

    /** Rig extents in rig space (+X right, +Z towards the user), design container.rig.extents. */
    const val MIN_X = -0.20f
    const val MAX_X = 0.25f
    const val MIN_Y = -0.36f
    const val MAX_Y = 0.17f

    /** Window estimate: 640 dp at ~1 mm/dp = 0.64 m wide, 1.75 m ahead. */
    const val WINDOW_W_M = 0.64f
    const val WINDOW_DIST_M = 1.75f

    fun place(head: HeadPose, leftDeg: Float = LEFT_DEG): Upright {
        val yaw = head.yaw(0f) + Math.toRadians(leftDeg.toDouble()).toFloat()
        val p = head.position + Yaw.dir(yaw) * DIST_M - Vec3(0f, BELOW_M, 0f)
        return Upright(p, faceYaw(p, head.position))
    }

    /** Where a rig would be facing [viewer] from [p] (drag release, D1: only about the vertical). */
    fun facing(p: Vec3, viewer: Vec3): Upright = Upright(p, faceYaw(p, viewer))

    /** Horizontal angle (deg, + = left of the heading) of a world point seen from the head along [headingYaw]. */
    fun bearingDeg(head: Vec3, headingYaw: Float, p: Vec3): Float {
        val d = Vec3(p.x - head.x, 0f, p.z - head.z)
        val a = Yaw.of(d) - headingYaw
        return Math.toDegrees(Yaw.wrap(a).toDouble()).toFloat()
    }

    /** Bearing of the rig's right outer edge (its +X extent) from the head: the side that faces the window. */
    fun rightEdgeDeg(head: HeadPose, rig: Upright): Float {
        val right = rig.rotation.rotate(Vec3(MAX_X, 0f, 0f))
        return bearingDeg(head.position, head.yaw(0f), rig.position + right)
    }

    /** Bearing of the window's left edge from the head (window square to the heading at [WINDOW_DIST_M]). */
    fun windowLeftEdgeDeg(): Float = Math.toDegrees(atan2(WINDOW_W_M / 2f, WINDOW_DIST_M).toDouble()).toFloat()

    /** Is the rig far from where you look now (design lifecycle: > 45° off or > 2.5 m)? */
    fun misplaced(head: HeadPose, rig: Vec3): Boolean {
        // the rig normally sits LEFT_DEG to the left: "off" = more than 45° away from that bearing
        val b = abs(Yaw.wrap(Math.toRadians((bearingDeg(head.position, head.yaw(0f), rig) - LEFT_DEG).toDouble()).toFloat()))
        val d = hypot(rig.x - head.position.x, rig.z - head.position.z)
        return Math.toDegrees(b.toDouble()) > 45.0 || d > 2.5f
    }
}

/**
 * "看正前方" before a re-center (design S30, D29): the button is on the window, so the head is aimed at the window when
 * it is pressed; wait for the heading to hold still (< 2° for 300 ms) and the head to be level, then sample.
 */
class SteadyGate(val holdMs: Long = 300L, val maxDriftDeg: Float = 2f, val maxPitchDeg: Float = 10f) {
    private var since = -1L
    private var refYaw = 0f
    var waiting = false
        private set

    fun start() {
        waiting = true
        since = -1L
    }

    fun cancel() {
        waiting = false
    }

    /** Returns true once (and stops waiting) when the head has been steady and level for [holdMs]. */
    fun step(now: Long, yawRad: Float, pitchDeg: Float?): Boolean {
        if (!waiting) return false
        if (pitchDeg == null || abs(pitchDeg) > maxPitchDeg) {
            since = -1L
            return false
        }
        if (since < 0 || abs(Math.toDegrees(Yaw.wrap(yawRad - refYaw).toDouble())) > maxDriftDeg) {
            since = now
            refYaw = yawRad
            return false
        }
        if (now - since >= holdMs) {
            waiting = false
            return true
        }
        return false
    }
}
