package com.kejing.cubetutor.domain.scan

import com.kejing.cubetutor.domain.Cube
import com.kejing.cubetutor.domain.CubeColor
import com.kejing.cubetutor.domain.Legality

/**
 * After six faces (design classifier ④⑤ + orientationFix):
 * 1. 54 cells are split into six groups of nine (balanced: each colour exactly nine), seeded with the six centres and
 *    refined three times; red / orange and white / yellow are separated by this step, not by fixed thresholds.
 * 2. The six groups get the six colour names by the cheapest one-to-one match of group means to [ColorModel.DEFAULTS].
 * 3. U and D may have been held turned: the 16 turn combinations are checked; the unturned one wins when legal,
 *    otherwise the single legal one. None (or several, none unturned) → the plain reading goes to the check (S07).
 * Faces painted by hand keep their colours and do not join the grouping.
 */
object CubeAssembler {
    data class Result(val cube: Cube, val amber: Set<Int>, val turnU: Int, val turnD: Int, val legal: Boolean)

    fun assemble(reads: Map<Int, FaceRead>): Result {
        require(reads.size == 6)
        val scanned = reads.values.all { it.labs != null }
        val colors = arrayOfNulls<CubeColor>(54)
        val conf = FloatArray(54) { 1f }
        if (scanned) group(reads, colors, conf)
        else for ((f, r) in reads) for (k in 0 until 9) {
            colors[f * 9 + k] = r.colors[k]
            conf[f * 9 + k] = r.conf[k]
        }
        val base = Array(54) { colors[it] ?: CubeColor.NONE }
        var pick: Pair<Int, Int>? = null
        val legalTurns = ArrayList<Pair<Int, Int>>()
        for (tu in 0 until 4) for (td in 0 until 4) {
            val c = Cube(turned(base, tu, td))
            if (Legality.check(c) == null) legalTurns += tu to td
        }
        if ((0 to 0) in legalTurns) pick = 0 to 0 else if (legalTurns.size == 1) pick = legalTurns[0]
        val (tu, td) = pick ?: (0 to 0)
        val cube = Cube(turned(base, tu, td))
        val conf2 = turnedF(conf, tu, td)
        val amber = (0 until 54).filter { conf2[it] < ColorModel.AMBER && it % 9 != 4 }.toSet()
        return Result(cube, amber, tu, td, pick != null)
    }

    private fun group(reads: Map<Int, FaceRead>, out: Array<CubeColor?>, conf: FloatArray) {
        val lab = Array(54) { i -> reads.getValue(i / 9).labs!![i % 9] }
        val means = Array(6) { f -> lab[f * 9 + 4] }
        var assign = IntArray(54) { it / 9 }
        repeat(4) {
            val a = IntArray(54) { -1 }
            val count = IntArray(6)
            for (f in 0 until 6) {
                a[f * 9 + 4] = f
                count[f]++
            }
            val pairs = ArrayList<Triple<Float, Int, Int>>()
            for (i in 0 until 54) if (i % 9 != 4) for (k in 0 until 6) pairs += Triple(lab[i].dist(means[k]), i, k)
            pairs.sortBy { it.first }
            for ((_, i, k) in pairs) if (a[i] < 0 && count[k] < 9) {
                a[i] = k
                count[k]++
            }
            assign = a
            for (k in 0 until 6) means[k] = Lab.mean((0 until 54).filter { assign[it] == k }.map { lab[it] })
        }
        // names: cheapest one-to-one match of group means to the default colours
        val six = CubeColor.SIX
        var bestCost = Float.MAX_VALUE
        var bestPerm = IntArray(6) { it }
        permute(IntArray(6) { it }, 0) { p ->
            var cost = 0f
            for (k in 0 until 6) cost += means[k].dist(ColorModel.DEFAULTS.getValue(six[p[k]]))
            if (cost < bestCost) {
                bestCost = cost
                bestPerm = p.copyOf()
            }
        }
        for (i in 0 until 54) {
            val k = assign[i]
            out[i] = six[bestPerm[k]]
            // confidence: own group vs the nearest other group
            val d1 = lab[i].dist(means[k])
            var d2 = Float.MAX_VALUE
            for (o in 0 until 6) if (o != k) d2 = minOf(d2, lab[i].dist(means[o]))
            conf[i] = if (d2 <= 0f) 0f else (1f - d1 / d2).coerceIn(0f, 1f)
        }
    }

    private fun permute(a: IntArray, k: Int, visit: (IntArray) -> Unit) {
        if (k == a.size) return visit(a)
        for (i in k until a.size) {
            a[k] = a[i].also { a[i] = a[k] }
            permute(a, k + 1, visit)
            a[k] = a[i].also { a[i] = a[k] }
        }
    }

    /** Facelet index k (row-major 3 x 3) after turning the face picture [t] quarter turns clockwise. */
    fun turnIndex(k: Int, t: Int): Int {
        var r = k / 3
        var c = k % 3
        repeat(t and 3) {
            val nr = c
            val nc = 2 - r
            r = nr
            c = nc
        }
        return r * 3 + c
    }

    private fun <T> turnedArr(src: Array<T>, dst: Array<T>, tu: Int, td: Int) {
        for (i in 0 until 54) {
            val f = i / 9
            val t = if (f == 0) tu else if (f == 3) td else 0
            dst[f * 9 + turnIndex(i % 9, t)] = src[i]
        }
    }

    fun turned(src: Array<CubeColor>, tu: Int, td: Int): Array<CubeColor> = src.copyOf().also { turnedArr(src, it, tu, td) }

    private fun turnedF(src: FloatArray, tu: Int, td: Int): FloatArray {
        val a = src.toTypedArray()
        val b = a.copyOf()
        turnedArr(a, b, tu, td)
        return b.toFloatArray()
    }
}
