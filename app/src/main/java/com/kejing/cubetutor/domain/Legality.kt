package com.kejing.cubetutor.domain

/**
 * The three checks before a lesson may start (design cubeSpec.legality, M5): ① colour count, ② pieces, ③ parity.
 * Colours are mapped to faces through the six centres, so any colour scheme works. Pure Kotlin.
 */
object Legality {
    enum class Kind { COUNT, PIECE, PARITY }

    /** Why a cube fails: the kind, the facelets to ring in red, and per-colour counts (count failures). */
    data class Failure(val kind: Kind, val suspects: List<Int>, val counts: Map<CubeColor, Int> = emptyMap())

    // Kociemba corner / edge facelets (1-based within the face in the comments: URF = U9 R1 F3 …)
    private fun f(face: Int, n: Int) = face * 9 + n - 1
    private const val U = 0
    private const val R = 1
    private const val F = 2
    private const val D = 3
    private const val L = 4
    private const val B = 5

    val cornerFacelets: Array<IntArray> =
        arrayOf(
            intArrayOf(f(U, 9), f(R, 1), f(F, 3)), intArrayOf(f(U, 7), f(F, 1), f(L, 3)), intArrayOf(f(U, 1), f(L, 1), f(B, 3)), intArrayOf(f(U, 3), f(B, 1), f(R, 3)),
            intArrayOf(f(D, 3), f(F, 9), f(R, 7)), intArrayOf(f(D, 1), f(L, 9), f(F, 7)), intArrayOf(f(D, 7), f(B, 9), f(L, 7)), intArrayOf(f(D, 9), f(R, 9), f(B, 7)),
        )
    val cornerFaces: Array<IntArray> =
        arrayOf(intArrayOf(U, R, F), intArrayOf(U, F, L), intArrayOf(U, L, B), intArrayOf(U, B, R), intArrayOf(D, F, R), intArrayOf(D, L, F), intArrayOf(D, B, L), intArrayOf(D, R, B))
    val edgeFacelets: Array<IntArray> =
        arrayOf(
            intArrayOf(f(U, 6), f(R, 2)), intArrayOf(f(U, 8), f(F, 2)), intArrayOf(f(U, 4), f(L, 2)), intArrayOf(f(U, 2), f(B, 2)),
            intArrayOf(f(D, 6), f(R, 8)), intArrayOf(f(D, 2), f(F, 8)), intArrayOf(f(D, 4), f(L, 8)), intArrayOf(f(D, 8), f(B, 8)),
            intArrayOf(f(F, 6), f(R, 4)), intArrayOf(f(F, 4), f(L, 6)), intArrayOf(f(B, 6), f(L, 4)), intArrayOf(f(B, 4), f(R, 6)),
        )
    val edgeFaces: Array<IntArray> =
        arrayOf(
            intArrayOf(U, R), intArrayOf(U, F), intArrayOf(U, L), intArrayOf(U, B), intArrayOf(D, R), intArrayOf(D, F), intArrayOf(D, L), intArrayOf(D, B),
            intArrayOf(F, R), intArrayOf(F, L), intArrayOf(B, L), intArrayOf(B, R),
        )

    /** Cubie model: corner / edge permutation and orientation (null when a piece does not exist). */
    data class Cubies(val cp: IntArray, val co: IntArray, val ep: IntArray, val eo: IntArray)

    /**
     * Checks [cube] (all 54 filled). [recent] = facelets in the order the user filled them, newest last (the count
     * check rings the most recent one of an over-counted colour, design legality ①). Null = legal.
     */
    fun check(cube: Cube, recent: List<Int> = emptyList()): Failure? {
        // ① count
        val counts = CubeColor.SIX.associateWith { c -> (0 until 54).count { cube[it] == c } }
        val centers = (0 until 6).map { cube.centerColor(it) }
        if (counts.values.any { it != 9 } || centers.toSet().size != 6 || centers.any { it == CubeColor.NONE } || !cube.filled) {
            val over = counts.filter { it.value > 9 }.keys
            val suspects =
                if (!cube.filled) (0 until 54).filter { cube[it] == CubeColor.NONE }
                else {
                    val newest = recent.reversed().firstOrNull { cube[it] in over && it % 9 != 4 }
                    listOfNotNull(newest ?: (0 until 54).firstOrNull { cube[it] in over && it % 9 != 4 })
                }
            return Failure(Kind.COUNT, suspects, counts)
        }
        val faceOf = centers.withIndex().associate { (i, c) -> c to i }
        val face = IntArray(54) { faceOf.getValue(cube[it]) }
        // ② pieces
        val cp = IntArray(8) { -1 }
        val co = IntArray(8)
        for (i in 0 until 8) {
            val fl = cornerFacelets[i]
            val ori = (0 until 3).firstOrNull { face[fl[it]] == U || face[fl[it]] == D }
            val c1 = if (ori != null) face[fl[(ori + 1) % 3]] else -1
            val c2 = if (ori != null) face[fl[(ori + 2) % 3]] else -1
            val j = if (ori == null) -1 else (0 until 8).firstOrNull { cornerFaces[it][0] == face[fl[ori]] && cornerFaces[it][1] == c1 && cornerFaces[it][2] == c2 } ?: -1
            if (j < 0) return Failure(Kind.PIECE, fl.toList())
            cp[i] = j
            co[i] = ori!!
        }
        val ep = IntArray(12) { -1 }
        val eo = IntArray(12)
        for (i in 0 until 12) {
            val fl = edgeFacelets[i]
            val a = face[fl[0]]
            val b = face[fl[1]]
            var found = -1
            for (j in 0 until 12) {
                if (edgeFaces[j][0] == a && edgeFaces[j][1] == b) { found = j; eo[i] = 0 }
                else if (edgeFaces[j][0] == b && edgeFaces[j][1] == a) { found = j; eo[i] = 1 }
                if (found >= 0) break
            }
            if (found < 0) return Failure(Kind.PIECE, fl.toList())
            ep[i] = found
        }
        dup(cp)?.let { return Failure(Kind.PIECE, it.flatMap { s -> cornerFacelets[s].toList() }) }
        dup(ep)?.let { return Failure(Kind.PIECE, it.flatMap { s -> edgeFacelets[s].toList() }) }
        // ③ parity
        val twist = co.sum() % 3
        val flip = eo.sum() % 2
        if (twist != 0 || flip != 0 || parity(cp) != parity(ep)) {
            val last = recent.lastOrNull()?.let { Facelets.faceOf(it) } ?: 2
            return Failure(Kind.PARITY, (0 until 9).map { last * 9 + it })
        }
        return null
    }

    /** Slots holding a piece that also sits elsewhere (both), or null. */
    private fun dup(p: IntArray): List<Int>? {
        val seen = HashMap<Int, Int>()
        for ((i, v) in p.withIndex()) {
            val prev = seen.put(v, i)
            if (prev != null) return listOf(prev, i)
        }
        return null
    }

    private fun parity(p: IntArray): Int {
        var s = 0
        for (i in p.indices) for (j in i + 1 until p.size) if (p[i] > p[j]) s++
        return s % 2
    }

    fun cubies(cube: Cube): Cubies? {
        if (check(cube) != null) return null
        val centers = (0 until 6).map { cube.centerColor(it) }
        val faceOf = centers.withIndex().associate { (i, c) -> c to i }
        val face = IntArray(54) { faceOf.getValue(cube[it]) }
        val cp = IntArray(8)
        val co = IntArray(8)
        for (i in 0 until 8) {
            val fl = cornerFacelets[i]
            val ori = (0 until 3).first { face[fl[it]] == U || face[fl[it]] == D }
            cp[i] = (0 until 8).first { cornerFaces[it][0] == face[fl[ori]] && cornerFaces[it][1] == face[fl[(ori + 1) % 3]] && cornerFaces[it][2] == face[fl[(ori + 2) % 3]] }
            co[i] = ori
        }
        val ep = IntArray(12)
        val eo = IntArray(12)
        for (i in 0 until 12) {
            val fl = edgeFacelets[i]
            for (j in 0 until 12) {
                if (edgeFaces[j][0] == face[fl[0]] && edgeFaces[j][1] == face[fl[1]]) { ep[i] = j; eo[i] = 0 }
                if (edgeFaces[j][0] == face[fl[1]] && edgeFaces[j][1] == face[fl[0]]) { ep[i] = j; eo[i] = 1 }
            }
        }
        return Cubies(cp, co, ep, eo)
    }
}
