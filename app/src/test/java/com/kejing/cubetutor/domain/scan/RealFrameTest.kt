package com.kejing.cubetutor.domain.scan

import com.kejing.cubetutor.domain.CubeColor
import com.kejing.cubetutor.domain.Lessons
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headset frames (spike 2026-11-17: PICO swan right-eye passthrough 640 x 480, warm dim office, a stickerless cube held
 * at the alignment frame; saved by the debug build's frame dump). Truth read off the pictures by hand.
 */
class RealFrameTest {
    // headset frames show the office (screens, people): they stay on the build machine, never in the public repo
    private val dir: File? = listOf(File("src/test/resources/scan-real/2026-11-17"), File("app/src/test/resources/scan-real/2026-11-17")).firstOrNull { it.isDirectory }

    /** File-name prefix (frame number + dump time) → the face's nine colours; null = turning, three faces visible. */
    private val truth =
        mapOf(
            "frame-000-grid0-1794975207582" to "ORRYRRRRY",
            "frame-020-grid0-1794975195600" to "OBBYYBYGW",
            "frame-020-grid0-1794975211027" to "ORRYRRRRY",
            "frame-040-grid0-1794975198892" to "ORRYRRRRY",
            "frame-040-grid0-1794975214299" to "ORRYRRRRY",
            "frame-060-grid0-1794975202159" to "ORRYRRRRY",
            "frame-060-grid0-1794975217588" to "ORRYRRRRY",
            "frame-080-grid0-1794975205453" to "ORRYRRRRY",
            "frame-080-grid0-1794975220881" to null,
            "frame-100-grid0-1794975224171" to "YYGGGBRYG",
            "frame-120-grid0-1794975227455" to "WGYBYYBBO",
            "frame-140-grid0-1794975230732" to "WGYBYYBBO",
            "frame-160-grid0-1794975234014" to "WGYBYYBBO",
            "frame-180-grid0-1794975237299" to "WGYBYYBBO",
        )

    /** Red and orange are told apart across faces (ScanSession.redOrange), not within one frame: compare them as one. */
    private fun ro(s: String) = s.replace('O', 'X').replace('R', 'X')

    @Test
    fun stickerlessCubeOnTheHeadset() {
        val dir = dir ?: return println("SCAN real: frames not on this machine, skipped")
        var found = 0
        var exact = 0
        for ((name, want) in truth) {
            val g = GridFinder.find(Png.read(File(dir, "$name.png")), GridFinder.Roi.FULL) ?: continue
            found++
            if (want == null) continue
            assertTrue("$name: a front view is square-on", GridFinder.squareOn(g))
            val m = ColorModel().withCentre(g.cells[4].lab).first
            val got = g.cells.joinToString("") { m.classify(it.lab).first.code.toString() }
            assertEquals(name, ro(want), ro(got))
            if (got == want) exact++
        }
        println("SCAN real: grid in $found / ${truth.size}, exact $exact")
        assertTrue("grid in $found / ${truth.size}", found >= 10)
        assertTrue("exact $exact", exact >= 9)
    }

    @Test
    fun turningCubeIsNotSquareOn() {
        val dir = dir ?: return
        val g = GridFinder.find(Png.read(File(dir, "frame-080-grid0-1794975220881.png")), GridFinder.Roi.FULL)
        if (g != null) assertTrue("three faces visible: not square-on", !GridFinder.squareOn(g))
    }

    /** A 3 x 3 at (x0, y0) with column step [sx] and row step [sy], all cells the given colours. */
    private fun at(x0: Float, y0: Float, sx: Float, sy: Float, vararg rgb: IntArray) =
        Grid(rgb.mapIndexed { i, c -> Cell(x0 + (i % 3) * sx, y0 + (i / 3) * sy, c[0], c[1], c[2]) }, (sx + sy) / 2)

    @Test
    fun faceTurnedSidewaysIsNeverTaken() {
        val w = intArrayOf(202, 207, 220)
        val s = ScanSession(Lessons.readFaces)
        s.wanted = Inference.faceOf(CubeColor.WHITE)
        // squeezed to 40 % of its height across: the cube is half-way through a quarter turn
        val g = at(100f, 100f, 10f, 25f, w, w, w, w, w, w, w, w, w)
        var t = 0L
        while (t < 3000) {
            assertTrue(s.onGrid(g, t) is ScanSession.Event.Live)
            t += 160
        }
        // square again: taken
        val (e, _) = hold(s, at(100f, 100f, 25f, 22f, w, w, w, w, w, w, w, w, w), t)
        assertNotNull(e)
    }

    @Test
    fun movingFaceStartsTheClockAgain() {
        val y = intArrayOf(220, 207, 8)
        val s = ScanSession(Lessons.readFaces)
        var t = 0L
        var x = 100f
        while (t < 3000) {
            // same colours, sliding 12 px (half a square) every frame
            assertTrue(s.onGrid(at(x, 100f, 25f, 22f, y, y, y, y, y, y, y, y, y), t) is ScanSession.Event.Live)
            x += 12f
            t += 160
        }
    }

    @Test
    fun confirmedFaceTeachesItsColours() {
        val m = ColorModel()
        val dimRed = Lab.ofRgb(220, 104, 12) // the most orange-looking headset red: nearer the default orange
        assertEquals(CubeColor.ORANGE, m.classify(dimRed).first)
        val labs = List(9) { if (it == 4) Lab.ofRgb(220, 98, 12) else dimRed }
        m.learn(List(9) { CubeColor.RED }, labs)
        assertEquals(CubeColor.RED, m.classify(Lab.ofRgb(220, 104, 12)).first)
        assertEquals(CubeColor.ORANGE, m.classify(Lab.ofRgb(220, 122, 8)).first)
    }

    private fun grid(vararg rgb: IntArray) = Grid(rgb.mapIndexed { i, c -> Cell((i % 3) * 20f, (i / 3) * 20f, c[0], c[1], c[2]) }, 20f)

    private fun hold(s: ScanSession, g: Grid, t0: Long): Pair<ScanSession.Event.Confirmed?, Long> {
        var t = t0
        while (t < t0 + 3000) {
            val e = s.onGrid(g, t)
            if (e is ScanSession.Event.Confirmed) return e to t
            t += 160
        }
        return null to t
    }

    @Test
    fun dimRedCentreIsRedWhenTheRedSideIsAsked() {
        // headset values (normalised): a dim red reads like orange on its own; asked for the red side, the centre is
        // red and the other squares are judged against it
        val red = intArrayOf(220, 87, 12)
        val orange = intArrayOf(220, 120, 8)
        val yellow = intArrayOf(220, 207, 8)
        val redFace = Inference.faceOf(CubeColor.RED)!!
        val s = ScanSession(Lessons.readFaces)
        s.wanted = redFace
        val (a, _) = hold(s, grid(orange, red, red, yellow, red, red, red, red, yellow), 0L)
        assertNotNull(a)
        assertEquals(redFace, a!!.read.face)
        assertEquals("ORRYRRRRY", a.read.colors.joinToString("") { it.code.toString() })
    }

    @Test
    fun anotherUnreadSideIsNotTakenAsTheAskedOne() {
        // asked for the red side, the user shows the blue one: not taken (shown with the hint), nothing read
        val blue = intArrayOf(88, 131, 220)
        val y = intArrayOf(220, 207, 8)
        val s = ScanSession(Lessons.readFaces)
        s.wanted = Inference.faceOf(CubeColor.RED)
        val (e, _) = hold(s, grid(blue, y, blue, y, blue, y, blue, y, blue), 0L)
        assertEquals(null, e)
        assertEquals(0, s.reads.size)
    }

    @Test
    fun sameSideShownAgainIsARepeat() {
        val red = intArrayOf(220, 87, 12)
        val yellow = intArrayOf(220, 207, 8)
        val s = ScanSession(Lessons.readFaces)
        val g = grid(red, red, red, yellow, red, red, red, red, yellow)
        val (a, t1) = hold(s, g, 0L)
        assertNotNull(a)
        val (b, _) = hold(s, g, t1 + 1000)
        assertEquals(null, b)
        assertEquals(1, s.reads.size)
    }
}
