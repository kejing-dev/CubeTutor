package com.kejing.cubetutor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** User 2026-11-17: every lesson step carries the why of its group, and that why is about the move really played. */
class LessonWhyTest {
    @Test
    fun everyGroupFitsTheCard() {
        // the why card has 4 lines for the purpose at 640 dp (emulator fit check 2026-11-18): zh ≈ 33 / en ≈ 68 a line
        for ((id, g) in Lessons.groups) {
            assertTrue("$id zh ${g.purpose.zh.length}", g.purpose.zh.length <= 120)
            assertTrue("$id en ${g.purpose.en.length}", g.purpose.en.length <= 255)
        }
    }

    @Test
    fun everyStepHasItsGroupAndTheLitTokenIsTheMove() {
        val solved = Cube.solved()
        val seen = HashSet<String>()
        for (seed in 1..60) {
            val c = solved.apply(Scrambler.make(20, seed.toLong()).joinToString(" "))
            for (st in Solver.solve(c)) {
                val g = Lessons.groups[st.group]
                assertNotNull("stage ${st.stage} ${st.move} ${st.reason}: no group", g)
                seen += st.group
                assertTrue("pos ${st.pos} of ${st.len}", st.pos in 0 until st.len)
                if (g!!.notation != null) {
                    val tokens = g.notation!!.split(" ").filter { it != "·" }
                    assertEquals("${st.group} length", tokens.size, st.len)
                    assertEquals("${st.group} move ${st.pos}", tokens[st.pos], st.move)
                    assertEquals("${st.group} explains every move", tokens.size, g.moves!!.size)
                }
                for (zh in listOf(true, false)) {
                    val w = Lessons.whyNow(st, zh)!!
                    assertTrue(w.move.isNotBlank() && w.purpose.isNotBlank())
                }
            }
        }
        // the seven stages' groups all came up
        for (g in listOf("DAISY", "DOWN", "U", "Y", "SEXY", "YC", "SWAP", "CYCLE", "TWIST")) assertTrue("$g never used", g in seen)
        assertTrue("no belt formula used", "RB" in seen || "LB" in seen)
    }
}
