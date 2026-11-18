package com.kejing.cubetutor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headset 2026-11-18: the lesson said the white cross was done one move early, then "planning" for stage 2 hung until
 * the app was killed (stage 2's search on a cube without the cross ran out of memory). The cube saved at that moment.
 */
class SolverGuardTest {
    private val saved = Cube.parse("BBGYYRRGOWYROROBRGWRGBBGGBYRWOBWWBWOWGBOORRYYWOOYGWYGY")!!

    @Test
    fun theSavedCubeStillNeedsTheCross() {
        assertEquals(1, Solver.firstOpenStage(saved))
    }

    @Test
    fun aStageWhoseEarlierStagesAreOpenFailsFastInsteadOfSearchingForever() {
        val t0 = System.currentTimeMillis()
        val r = runCatching { Solver.stage(saved, 2) }
        assertTrue("stage 2 must refuse", r.isFailure && r.exceptionOrNull() is IllegalStateException)
        assertTrue("refused in ${System.currentTimeMillis() - t0} ms", System.currentTimeMillis() - t0 < 1000)
    }

    @Test
    fun theFirstOpenStagePlansAndReallyFinishesIt() {
        val steps = Solver.stage(saved, 1)
        val after = saved.apply(steps.joinToString(" ") { it.move })
        assertTrue(Solver.stageDone(after, 1))
    }
}
