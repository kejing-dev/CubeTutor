package com.kejing.cubetutor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design panelGeometryCheck.unitTest ⑦ ⑧ / mustPass M1 M2 (profile D17 补: ≥ 4 cm, ≥ 2°, gap ≥ 1.5 cm and ≥ 0.3 x). */
class ReadPoseTest {
    @Test
    fun stickerSpacingWhileReading() {
        val gap = ReadPose.stickerGap()
        assertTrue("gap $gap", gap >= 0.015f && gap >= 0.3f * ReadPose.TILE)
        assertTrue("tile ${ReadPose.TILE}", ReadPose.TILE >= 0.04f)
        assertTrue("angle ${ReadPose.angleDeg(ReadPose.TILE)}", ReadPose.angleDeg(ReadPose.TILE) >= 2.0)
        assertEquals(0.020f, gap, 1e-4f)
    }

    @Test
    fun onlyOneFaceIsHittableWhileReading() {
        for (target in 0 until 6) {
            val on = (0 until 54).filter { ReadPose.hittable(target, it) }
            assertEquals(9, on.size)
            assertTrue(on.all { Facelets.faceOf(it) == target })
        }
        assertEquals(54, (0 until 54).count { ReadPose.hittable(null, it) })
    }

    @Test
    fun neighbourStickersOnOneFaceDoNotOverlap() {
        // centres of the 9 stickers of a face in the loosened pose, pairwise gap between collider edges
        for (a in 0 until 9) for (b in a + 1 until 9) {
            val dx = (a % 3 - b % 3) * ReadPose.SP_READ
            val dy = (a / 3 - b / 3) * ReadPose.SP_READ
            val edge = maxOf(Math.abs(dx), Math.abs(dy)) - ReadPose.TILE
            assertTrue("$a-$b edge gap $edge", edge >= 0.015f - 1e-6f)
        }
    }

    @Test
    fun handleSpacing() {
        val gap = ReadPose.handleGap()
        assertTrue("handle gap $gap", gap >= 0.015f && gap >= 0.3f * ReadPose.HANDLE_HIT)
        assertTrue(ReadPose.angleDeg(ReadPose.HANDLE_HIT) >= 2.0)
    }
}
