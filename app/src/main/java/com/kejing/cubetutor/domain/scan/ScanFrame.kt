package com.kejing.cubetutor.domain.scan

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * One camera (or injected) frame: packed RGB, 3 bytes per pixel, row 0 at the top, not mirrored (what the eye sees).
 * The right-eye readback is 640 x 480 (design cameraCapability.route); injected pictures can be any size.
 */
class ScanFrame(val w: Int, val h: Int, val rgb: ByteArray, val tMs: Long) {
    init {
        require(rgb.size >= w * h * 3) { "frame ${w}x$h has ${rgb.size} bytes" }
    }

    fun r(x: Int, y: Int): Int = rgb[(y * w + x) * 3].toInt() and 0xFF

    fun g(x: Int, y: Int): Int = rgb[(y * w + x) * 3 + 1].toInt() and 0xFF

    fun b(x: Int, y: Int): Int = rgb[(y * w + x) * 3 + 2].toInt() and 0xFF

    /** Mean of all bytes: an all-zero readback (troubleshooting「Readback returns all zeros」) is not a frame. */
    fun meanLevel(step: Int = 97): Double {
        var s = 0L
        var n = 0
        var i = 0
        while (i < w * h * 3) {
            s += rgb[i].toInt() and 0xFF
            n++
            i += step
        }
        return if (n == 0) 0.0 else s.toDouble() / n
    }
}

/** CIE Lab (D65), the space the colours are compared in: hue lives in a/b, so light level matters less. */
data class Lab(val l: Float, val a: Float, val b: Float) {
    val chroma: Float
        get() = hypot(a, b)

    val hueDeg: Float
        get() = ((Math.toDegrees(atan2(b.toDouble(), a.toDouble())) + 360.0) % 360.0).toFloat()

    /** Distance with the lightness weighted down (warm / dim rooms move L a lot, the hue much less). */
    fun dist(o: Lab): Float {
        val dl = (l - o.l) * L_WEIGHT
        val da = a - o.a
        val db = b - o.b
        return sqrt(dl * dl + da * da + db * db)
    }

    companion object {
        const val L_WEIGHT = 0.55f

        private fun lin(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }

        private fun f(t: Double): Double = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0

        fun ofRgb(r: Int, g: Int, b: Int): Lab {
            val rl = lin(r)
            val gl = lin(g)
            val bl = lin(b)
            val x = (0.4124 * rl + 0.3576 * gl + 0.1805 * bl) / 0.95047
            val y = 0.2126 * rl + 0.7152 * gl + 0.0722 * bl
            val z = (0.0193 * rl + 0.1192 * gl + 0.9505 * bl) / 1.08883
            val fx = f(x)
            val fy = f(y)
            val fz = f(z)
            return Lab((116.0 * fy - 16.0).toFloat(), (500.0 * (fx - fy)).toFloat(), (200.0 * (fy - fz)).toFloat())
        }

        fun mean(list: List<Lab>): Lab {
            if (list.isEmpty()) return Lab(0f, 0f, 0f)
            return Lab(list.map { it.l }.average().toFloat(), list.map { it.a }.average().toFloat(), list.map { it.b }.average().toFloat())
        }
    }
}

/** One sticker the grid finder sampled: its centre in the frame and its median colour. */
data class Cell(val x: Float, val y: Float, val r: Int, val g: Int, val b: Int) {
    val lab: Lab by lazy { Lab.ofRgb(r, g, b) }
}

/** A 3 x 3 grid found in a frame: cells row by row as you look at the face (row 0 on top), and the cell pitch in px. */
data class Grid(val cells: List<Cell>, val pitch: Float) {
    init {
        require(cells.size == 9)
    }

    val center: Cell
        get() = cells[4]
}
