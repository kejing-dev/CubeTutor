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

    private val sums = HashMap<CubeColor, FloatArray>() // l, a, b, n of the squares the user confirmed

    /**
     * A face the user confirmed (or fixed): every square teaches its colour's reference, averaged over all confirmed
     * squares so far (user 2026-11-17: red / orange get better face by face).
     */
    fun learn(colors: List<CubeColor>, labs: List<Lab>) {
        for (k in colors.indices) {
            val c = colors[k]
            if (c == CubeColor.NONE) continue
            val s = sums.getOrPut(c) { FloatArray(4) }
            s[0] += labs[k].l
            s[1] += labs[k].a
            s[2] += labs[k].b
            s[3] += 1f
        }
        for ((c, s) in sums) refs[c] = Lab(s[0] / s[3], s[1] / s[3], s[2] / s[3])
    }

    /** A copy where [colour]'s reference is the measured [centre] (the face asked for: its centre colour is known). */
    fun withCentreAs(centre: Lab, colour: CubeColor): ColorModel {
        val m = ColorModel()
        m.refs.clear()
        m.refs.putAll(refs)
        m.calibrate(colour, centre)
        return m
    }

    /** A copy whose [centre]'s colour reference is the measured centre (each face is read against its own centre). */
    fun withCentre(centre: Lab): Pair<ColorModel, CubeColor> {
        val c = classify(centre).first
        val m = ColorModel()
        m.refs.clear()
        m.refs.putAll(refs)
        m.calibrate(c, centre)
        return m to c
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

        /**
         * Typical tile colours after GridFinder's white balance + brightness normalisation (brightest channel 220),
         * measured on the headset (spike 2026-11-17, PICO swan right-eye passthrough, warm office light, stickerless
         * cube). Red reads orange-ish on this camera (G/R 0.2–0.47 vs orange 0.55): a red face seen before the orange one may
         * first be named orange; the face's own centre (whose colour is known: the face asked for) is the reference.
         * Every confirmed face's centre replaces its colour's reference (room light learnt as you go).
         */
        val DEFAULTS: Map<CubeColor, Lab> =
            mapOf(
                CubeColor.WHITE to Lab.ofRgb(202, 207, 220),
                CubeColor.YELLOW to Lab.ofRgb(220, 206, 8),
                CubeColor.RED to Lab.ofRgb(220, 70, 10),
                CubeColor.ORANGE to Lab.ofRgb(220, 120, 8),
                CubeColor.BLUE to Lab.ofRgb(88, 131, 220),
                CubeColor.GREEN to Lab.ofRgb(138, 220, 145),
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
