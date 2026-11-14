package com.kejing.cubetutor.domain.scan

import com.kejing.cubetutor.domain.Cube
import com.kejing.cubetutor.domain.CubeColor
import com.kejing.cubetutor.domain.Legality

/**
 * "Scan only a few faces, infer the rest" (user addendum 2026-11-11). The six centres fix the faces and the standard
 * colour scheme (W–Y, R–O, B–G, fixed handedness = [Cube.SCHEME]), so the 8 corner and 12 edge pieces are known; the
 * unread stickers are whatever the pieces must be for the read stickers to fit, with every piece used once and
 * twist / flip / permutation parity right ([Legality.check]). Unique → fill them in and stop scanning; several → name the
 * unread face whose stickers differ most between the candidates; none → the reading contradicts a standard cube
 * (misread or non-standard scheme) → the user confirms / corrects.
 *
 * Pure Kotlin, JVM-tested. Backtracking over positions, most-constrained first; capped at [CAP] solutions / [MAX_NODES].
 */
object Inference {
    const val CAP = 24
    const val MAX_NODES = 2_000_000

    sealed class Result {
        /** The one cube the read stickers allow. [inferred] = facelets that were not read. */
        data class Unique(val cube: Cube, val inferred: Set<Int>) : Result()

        /** Several cubes fit ([count] ≥ 2, capped); [askFace] = the unread face that tells them apart best. */
        data class Ambiguous(val count: Int, val askFace: Int) : Result()

        /** No standard cube fits what was read. */
        object None : Result()
    }

    private val solved = Cube.solved()
    private val cornerPieces: List<Array<CubeColor>> = Legality.cornerFacelets.map { f -> Array(3) { solved[f[it]] } }
    private val edgePieces: List<Array<CubeColor>> = Legality.edgeFacelets.map { f -> Array(2) { solved[f[it]] } }

    /** The URFDLB face whose centre has this colour on a standard cube (centre colour defines the face). */
    fun faceOf(center: CubeColor): Int? = Cube.SCHEME.entries.firstOrNull { it.value == center }?.key

    /**
     * [known]: 54 entries, null = not read. Centres are filled from the scheme when missing. Read centres that
     * disagree with the scheme make the result [Result.None].
     */
    fun infer(known: Array<CubeColor?>): Result {
        val k = known.copyOf()
        for (f in 0 until 6) {
            val c = k[f * 9 + 4]
            if (c == null) k[f * 9 + 4] = solved[f * 9 + 4] else if (c != solved[f * 9 + 4]) return Result.None
        }
        val solutions = solve(k)
        if (solutions.isEmpty()) return Result.None
        val unread = (0 until 54).filter { known[it] == null }.toSet()
        if (solutions.size == 1) return Result.Unique(solutions[0], unread)
        // which unread face differs most between the candidates
        val readFaces = (0 until 6).filter { f -> (0 until 9).all { known[f * 9 + it] != null } }.toSet()
        val ask =
            (0 until 6).filter { it !in readFaces }.maxByOrNull { f -> solutions.map { s -> (0 until 9).map { s[f * 9 + it] } }.toSet().size }
                ?: 0
        return Result.Ambiguous(solutions.size, ask)
    }

    private fun solve(k: Array<CubeColor?>): List<Cube> {
        // candidate (piece, orientation) per position, filtered by the read stickers
        val cornerCands = Legality.cornerFacelets.map { pos ->
            val list = ArrayList<IntArray>()
            for (p in cornerPieces.indices) for (o in 0 until 3) {
                if ((0 until 3).all { j -> k[pos[j]] == null || k[pos[j]] == cornerPieces[p][(j + o) % 3] }) list += intArrayOf(p, o)
            }
            list
        }
        val edgeCands = Legality.edgeFacelets.map { pos ->
            val list = ArrayList<IntArray>()
            for (p in edgePieces.indices) for (o in 0 until 2) {
                if ((0 until 2).all { j -> k[pos[j]] == null || k[pos[j]] == edgePieces[p][(j + o) % 2] }) list += intArrayOf(p, o)
            }
            list
        }
        if (cornerCands.any { it.isEmpty() } || edgeCands.any { it.isEmpty() }) return emptyList()
        val cOrder = cornerCands.indices.sortedBy { cornerCands[it].size }
        val eOrder = edgeCands.indices.sortedBy { edgeCands[it].size }
        val out = ArrayList<Cube>()
        val cell = Array(54) { k[it] ?: CubeColor.NONE }
        val usedC = BooleanArray(8)
        val usedE = BooleanArray(12)
        var nodes = 0
        var cTwist = 0
        var eFlip = 0

        fun edges(d: Int) {
            if (out.size >= CAP || nodes > MAX_NODES) return
            if (d == eOrder.size) {
                if (eFlip % 2 != 0) return
                val c = Cube(cell.copyOf())
                if (Legality.check(c) == null) out += c
                return
            }
            val pos = eOrder[d]
            for (cand in edgeCands[pos]) {
                val p = cand[0]
                if (usedE[p]) continue
                nodes++
                usedE[p] = true
                eFlip += cand[1]
                val f = Legality.edgeFacelets[pos]
                for (j in 0 until 2) cell[f[j]] = edgePieces[p][(j + cand[1]) % 2]
                edges(d + 1)
                eFlip -= cand[1]
                usedE[p] = false
            }
        }

        fun corners(d: Int) {
            if (out.size >= CAP || nodes > MAX_NODES) return
            if (d == cOrder.size) {
                if (cTwist % 3 != 0) return
                edges(0)
                return
            }
            val pos = cOrder[d]
            for (cand in cornerCands[pos]) {
                val p = cand[0]
                if (usedC[p]) continue
                nodes++
                usedC[p] = true
                cTwist += cand[1]
                val f = Legality.cornerFacelets[pos]
                for (j in 0 until 3) cell[f[j]] = cornerPieces[p][(j + cand[1]) % 3]
                corners(d + 1)
                cTwist -= cand[1]
                usedC[p] = false
            }
        }
        corners(0)
        return out
    }
}
