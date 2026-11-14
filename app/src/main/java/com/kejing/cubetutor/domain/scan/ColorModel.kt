package com.kejing.cubetutor.domain.scan

import com.kejing.cubetutor.domain.CubeColor

/**
 * Six reference colours and nearest-reference classification (design classifier ③⑤; reference sample ColorClassifier:
 * nearest calibrated face centre). Starts from typical sticker colours as a headset camera sees them; every confirmed
 * face replaces its centre colour's reference with the measured centre, so the room light is learnt as you go.
 */
class ColorModel {
    private val refs = HashMap<CubeColor, Lab>(DEFAULTS)

    fun ref(c: CubeColor): Lab = refs.getValue(c)

    fun calibrate(c: CubeColor, lab: Lab) {
        if (c != CubeColor.NONE) refs[c] = lab
    }

    /** Nearest colour and a confidence in 0..1 (1 − nearest / second-nearest distance; amber below [AMBER]). */
    fun classify(lab: Lab): Pair<CubeColor, Float> {
        var best = CubeColor.NONE
        var d1 = Float.MAX_VALUE
        var d2 = Float.MAX_VALUE
        for ((c, ref) in refs) {
            val d = lab.dist(ref)
            if (d < d1) {
                d2 = d1
                d1 = d
                best = c
            } else if (d < d2) d2 = d
        }
        val conf = if (d2 <= 0f) 0f else (1f - d1 / d2).coerceIn(0f, 1f)
        return best to conf
    }

    companion object {
        /** Below this the cell is drawn amber and asked about in S08 (design: distance ratio > 0.6). */
        const val AMBER = 0.4f

        /** Typical sticker colours in a passthrough frame (sRGB), as Lab. */
        val DEFAULTS: Map<CubeColor, Lab> =
            mapOf(
                CubeColor.WHITE to Lab.ofRgb(225, 228, 225),
                CubeColor.YELLOW to Lab.ofRgb(225, 205, 35),
                CubeColor.RED to Lab.ofRgb(185, 25, 40),
                CubeColor.ORANGE to Lab.ofRgb(245, 115, 30),
                CubeColor.BLUE to Lab.ofRgb(20, 75, 185),
                CubeColor.GREEN to Lab.ofRgb(25, 160, 75),
            )

        /** Opposite colours on a standard cube (white–yellow, red–orange, blue–green). */
        fun opposite(c: CubeColor): CubeColor =
            when (c) {
                CubeColor.WHITE -> CubeColor.YELLOW
                CubeColor.YELLOW -> CubeColor.WHITE
                CubeColor.RED -> CubeColor.ORANGE
                CubeColor.ORANGE -> CubeColor.RED
                CubeColor.BLUE -> CubeColor.GREEN
                CubeColor.GREEN -> CubeColor.BLUE
                CubeColor.NONE -> CubeColor.NONE
            }

        /** Axis of each colour on a standard (WCA) cube held yellow up, red front, green right: Cube.SCHEME. */
        private val AXIS: Map<CubeColor, IntArray> =
            mapOf(
                CubeColor.YELLOW to intArrayOf(0, 1, 0),
                CubeColor.WHITE to intArrayOf(0, -1, 0),
                CubeColor.RED to intArrayOf(0, 0, 1),
                CubeColor.ORANGE to intArrayOf(0, 0, -1),
                CubeColor.GREEN to intArrayOf(1, 0, 0),
                CubeColor.BLUE to intArrayOf(-1, 0, 0),
            )

        /** On a standard cube with [up] on top and [front] towards you, the colour on the right (null: same axis). */
        fun rightOf(up: CubeColor, front: CubeColor): CubeColor? {
            val u = AXIS[up] ?: return null
            val f = AXIS[front] ?: return null
            val r = intArrayOf(u[1] * f[2] - u[2] * f[1], u[2] * f[0] - u[0] * f[2], u[0] * f[1] - u[1] * f[0])
            return AXIS.entries.firstOrNull { it.value.contentEquals(r) }?.key
        }
    }
}
