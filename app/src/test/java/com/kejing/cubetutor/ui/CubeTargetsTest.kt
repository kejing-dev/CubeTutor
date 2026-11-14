package com.kejing.cubetutor.ui

import com.kejing.cubetutor.domain.Facelets
import com.kejing.cubetutor.domain.Handle
import com.kejing.cubetutor.domain.Row
import com.kejing.cubetutor.ui.input.CubeNames
import com.kejing.cubetutor.ui.input.CubeTarget
import com.kejing.cubetutor.ui.input.parseLoggedPath
import com.kejing.cubetutor.ui.input.resolvePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** board-input 命中回放 (build.md 2026-11-06): every hit path — the collider itself or a visible child — resolves to its target. */
class CubeTargetsTest {
    private fun r(vararg chain: String) = resolvePath(chain.toList(), CubeNames::resolveName)

    @Test
    fun everyStickerResolvesFromItsTile() {
        for (i in 0 until 54) {
            assertEquals(CubeTarget.Sticker(i), r(CubeNames.sticker(i), "cubie:1,1,1", "cubeRoot", "presentRoot", "cubeRig", "stageRoot"))
        }
    }

    @Test
    fun handlesResolveFromLabelAndPlate() {
        for (h in Handle.every) {
            val name = CubeNames.handle(h)
            assertEquals(CubeTarget.HandleT(h), r(CubeNames.label(name), name, "presentRoot", "cubeRig"))
            assertEquals(CubeTarget.HandleT(h), r(CubeNames.label("plate"), name, "cubeRig"))
            assertEquals(CubeTarget.HandleT(h), r(name))
        }
        assertEquals(CubeTarget.HandleT(Handle(Row.COL, 1)), r("h:col+1"))
        assertEquals(CubeTarget.HandleT(Handle(Row.ROW, Handle.ALL)), r("h:rowAll"))
    }

    @Test
    fun arrowsAndTray() {
        assertEquals(CubeTarget.Arrow(true), r(CubeNames.label("dp:a"), "dp:a", "dpadBar", "cubeRig"))
        assertEquals(CubeTarget.Arrow(false), r(CubeNames.label("arrowPlate"), "dp:b", "dpadBar"))
        assertEquals(CubeTarget.Tray, r(CubeNames.label("trayPlate"), "tray", "cubeRig"))
    }

    @Test
    fun bodiesAndUnknownNamesAreNotTargets() {
        assertNull(r("body", "cubie:0,0,1", "cubeRoot"))
        assertNull(r("st:X9"))
        assertNull(r("h:row+7"))
        assertNull(r("outline", "ghost:0"))
    }

    @Test
    fun replayLoggedPaths() {
        // the shape of `TAP src=… path=a < b < c` lines from the board-input log
        val logged = listOf(
            "st:F5 < cubie:0,0,1 < cubeRoot < presentRoot < cubeRig < stageRoot" to CubeTarget.Sticker(Facelets.F + 4),
            "lbl:h:col+1 < h:col+1 < presentRoot < cubeRig" to CubeTarget.HandleT(Handle(Row.COL, 1)),
            "lbl:plate < h:depth-1 < cubeRig < stageRoot" to CubeTarget.HandleT(Handle(Row.DEPTH, -1)),
            "lbl:dp:b < dp:b < dpadBar < cubeRig" to CubeTarget.Arrow(false),
            "lbl:tray < tray < cubeRig" to CubeTarget.Tray,
        )
        for ((path, want) in logged) assertEquals(path, want, resolvePath(parseLoggedPath(path), CubeNames::resolveName))
    }
}
