package com.kejing.cubetutor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.1.2 replay of the headset report 「点了魔方旁边的按钮点一下好像就取消了」: one trigger press arrived as the system
 * pointer tap and again as the app's own trigger-on-ray, 20–60 ms apart; a handle tap toggles, so it picked and un-picked.
 */
class TapDeduperTest {
    private val right = Handle(Row.COL, 1)

    /** Feeds (time ms, target) through the dedupe and the real handle-tap rule; returns the pick after each accepted tap. */
    private fun replay(events: List<Pair<Long, Handle>>): Selection? {
        val d = TapDeduper()
        var sel: Selection? = null
        for ((t, h) in events) if (d.accept("HandleT(${h.id})", t)) sel = Handles.tap(sel, h).selection
        return sel
    }

    @Test
    fun onePressSeenTwiceStaysPicked() {
        // system tap at 0 ms, the same press again 40 ms later (the old double path)
        val sel = replay(listOf(0L to right, 40L to right))
        assertNotNull("one press must leave the handle picked", sel)
        assertEquals(setOf(1), sel!!.layers)
    }

    @Test
    fun aDeliberateSecondTapStillUnpicks() {
        // 再点一次取消: a second press 600 ms later toggles it off (design input.handles)
        assertNull(replay(listOf(0L to right, 600L to right)))
    }

    @Test
    fun twoHandlesQuicklyBothCount() {
        // multi-select in one row: Right then Mid 100 ms apart are two presses
        val sel = replay(listOf(0L to right, 100L to Handle(Row.COL, 0)))
        assertEquals(setOf(1, 0), sel!!.layers)
    }

    @Test
    fun windowEdges() {
        val d = TapDeduper()
        assertTrue(d.accept("a", 1000))
        assertFalse(d.accept("a", 1000 + TapDeduper.WINDOW_MS - 1))
        assertTrue(d.accept("a", 1000 + TapDeduper.WINDOW_MS + 1))
    }
}
