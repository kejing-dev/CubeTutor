package com.kejing.cubetutor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeTest {
    private val solved = Cube.solved()

    @Test
    fun solvedIsLegalAndSolved() {
        assertTrue(solved.isSolved)
        assertNull(Legality.check(solved))
    }

    @Test
    fun everyQuarterTurnHasOrderFour() {
        for (m in listOf("U", "D", "R", "L", "F", "B", "M", "E", "S", "x", "y", "z")) {
            var c = solved.apply("R U F") // a non-trivial start
            val start = c
            repeat(4) { c = c.apply(m) }
            assertEquals("$m^4", start, c)
            assertFalse("$m changes the cube", start.apply(m) == start)
        }
    }

    @Test
    fun notationMatchesTheDesign() {
        // U = top layer to the LEFT: the front row's stickers go to the left face
        val u = solved.apply("U")
        val frontTopColor = solved[Facelets.F]
        assertEquals(frontTopColor, u[Facelets.L]) // L1 (top-left of L) shows what F1 showed
        // R = right layer UP: front-right column goes to the top
        val r = solved.apply("R")
        assertEquals(solved[Facelets.F + 2], r[Facelets.U + 2])
        // F = clockwise looking at F: top row goes to the right face
        val f = solved.apply("F")
        assertEquals(solved[Facelets.U + 6], f[Facelets.R])
        // y = whole cube left: the right face comes to the front
        val y = solved.apply("y")
        assertEquals(solved.centerColor(1), y.centerColor(2))
        // inverses
        assertEquals(solved, solved.apply("R U R' U' U R U' R'"))
        assertEquals(solved, solved.apply("F2 F2"))
    }

    @Test
    fun sexyMoveSixTimesIsIdentity() {
        var c = solved
        repeat(6) { c = c.apply(Solver.SEXY) }
        assertEquals(solved, c)
    }

    @Test
    fun scramblesAreLegalAndDoNotCancel() {
        for (seed in 0L until 200L) {
            val s = Scrambler.make(25, seed)
            assertEquals(25, s.size)
            for (i in 1 until s.size) assertTrue("same face twice: $s", s[i][0] != s[i - 1][0])
            assertNull(Legality.check(solved.apply(s.joinToString(" "))))
        }
    }

    // ------------------------------------------------------------------ legality: three kinds × 20 (design acceptance.qa)

    @Test
    fun countFailures() {
        for (seed in 0L until 20L) {
            val c = solved.apply(Scrambler.make(20, seed).joinToString(" "))
            val i = (seed.toInt() * 7 + 1) % 54
            val idx = if (i % 9 == 4) i + 1 else i
            val other = CubeColor.SIX.first { it != c[idx] }
            val bad = c.with(idx, other)
            val f = Legality.check(bad, listOf(idx))
            assertNotNull(f)
            assertEquals(Legality.Kind.COUNT, f!!.kind)
            assertEquals(listOf(idx), f.suspects)
        }
    }

    @Test
    fun pieceFailures() {
        // swap two stickers of different pieces that keep the counts: a corner sticker with an edge sticker of the same colours swapped
        var n = 0
        for (seed in 0L until 200L) {
            if (n >= 20) break
            val c = solved.apply(Scrambler.make(20, seed).joinToString(" "))
            // exchange the U-facing sticker of corner URF with the U-facing sticker of edge UF when colours differ
            val a = Legality.cornerFacelets[seed.toInt() % 8][0]
            val b = Legality.edgeFacelets[(seed.toInt() / 8) % 12][1]
            if (c[a] == c[b]) continue
            val bad = c.with(a, c[b]).with(b, c[a])
            val f = Legality.check(bad) ?: continue
            if (f.kind != Legality.Kind.PIECE) continue
            assertTrue(f.suspects.isNotEmpty())
            n++
        }
        assertEquals(20, n)
    }

    @Test
    fun parityFailures() {
        var n = 0
        for (seed in 0L until 20L) {
            val c = solved.apply(Scrambler.make(20, seed).joinToString(" "))
            // flip one edge in place (both stickers swapped): count and pieces stay fine, the flip sum is odd
            val e = Legality.edgeFacelets[seed.toInt() % 12]
            val flipped = c.with(e[0], c[e[1]]).with(e[1], c[e[0]])
            val f = Legality.check(flipped, listOf(e[0]))
            assertNotNull(f)
            assertEquals(Legality.Kind.PARITY, f!!.kind)
            // twist one corner in place
            val k = Legality.cornerFacelets[seed.toInt() % 8]
            val twisted = c.with(k[0], c[k[1]]).with(k[1], c[k[2]]).with(k[2], c[k[0]])
            assertEquals(Legality.Kind.PARITY, Legality.check(twisted)!!.kind)
            n++
        }
        assertEquals(20, n)
    }

    // ------------------------------------------------------------------ the tutor on 1000 random scrambles (design cubeSpec.solver.limits)

    @Test
    fun thousandScramblesAllSolveWithinLimits() {
        val perStage = Array(8) { ArrayList<Int>() }
        val t0 = System.nanoTime()
        var worstMs = 0L
        for (seed in 0L until 1000L) {
            val scramble = Scrambler.make(25, seed).joinToString(" ")
            var c = solved.apply(scramble)
            for (k in 1..7) {
                val s0 = System.nanoTime()
                val steps = Solver.stage(c, k)
                worstMs = maxOf(worstMs, (System.nanoTime() - s0) / 1_000_000)
                assertTrue("L$k seed $seed: ${steps.size} > ${Solver.LIMIT[k]}", steps.size <= Solver.LIMIT.getValue(k))
                assertTrue(steps.all { it.move[0] in "UDRLFy" })
                c = c.apply(steps.joinToString(" ") { it.move })
                assertTrue("L$k not done seed $seed", Solver.stageDone(c, k))
                perStage[k] += steps.size
            }
            assertTrue("seed $seed not solved", c.isSolved)
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        for (k in 1..7) {
            val v = perStage[k].sorted()
            println("STAGE L$k median=${v[v.size / 2]} p90=${v[v.size * 9 / 10]} max=${v.last()}")
        }
        println("SOLVER 1000 scrambles in $ms ms, worst stage $worstMs ms")
    }

    @Test
    fun orientBringsWhiteDown() {
        for (pre in listOf("", "x", "x2", "z", "z'", "y x", "x' y2")) {
            val c = if (pre.isEmpty()) solved else solved.apply(pre)
            val turns = Solver.orient(c, CubeColor.RED)!!
            val o = c.apply(turns.joinToString(" "))
            assertEquals(CubeColor.WHITE, o.centerColor(3))
            assertEquals(CubeColor.RED, o.centerColor(2))
        }
    }

    @Test
    fun stageSkipsWhatIsDone() {
        assertTrue(Solver.stage(solved, 1).isEmpty())
        assertEquals(8, Solver.firstOpenStage(solved))
        val c = solved.apply("U")
        assertEquals(5, Solver.firstOpenStage(c))
    }
}
