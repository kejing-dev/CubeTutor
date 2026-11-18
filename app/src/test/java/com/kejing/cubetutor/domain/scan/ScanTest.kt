package com.kejing.cubetutor.domain.scan

import com.kejing.cubetutor.domain.Cube
import com.kejing.cubetutor.domain.CubeColor
import com.kejing.cubetutor.domain.Lessons
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design emulatorVerification.unitTests ①–⑤ on the synthetic photo sets (tools/make_scan_sets.py →
 * app/src/debug/assets/scan) and, when present, real headset frames (app/src/test/resources/scan-real/, spike S3).
 */
class ScanTest {
    private val root = listOf(File("src/debug/assets/scan"), File("app/src/debug/assets/scan")).first { it.isDirectory }

    private fun frame(f: File, t: Long = 0L): ScanFrame = Png.read(f, t)

    private fun shifted(f: ScanFrame, dx: Int, dy: Int, t: Long): ScanFrame {
        val out = ByteArray(f.rgb.size)
        for (y in 0 until f.h) for (x in 0 until f.w) {
            val sx = (x - dx).coerceIn(0, f.w - 1)
            val sy = (y - dy).coerceIn(0, f.h - 1)
            System.arraycopy(f.rgb, (sy * f.w + sx) * 3, out, (y * f.w + x) * 3, 3)
        }
        return ScanFrame(f.w, f.h, out, t)
    }

    private fun truth(set: String): Cube = Cube.parse(File(root, "$set/truth.txt").readText().trim())!!

    private val turns = mapOf("setA" to (1 to 2))

    /** The nine colours the picture of face n shows (U / D drawn turned in setA). */
    private fun pictured(set: String, n: Int): List<CubeColor> {
        val f = Lessons.readFaces[n - 1]
        val c = truth(set)
        val nine = (0 until 9).map { c[f * 9 + it] }
        val t = when (f) { 0 -> turns[set]?.first ?: 0; 3 -> turns[set]?.second ?: 0; else -> 0 }
        // the generator turns the picture clockwise t times: cell k of the picture shows the facelet at turnIndex⁻¹
        var g = nine
        repeat(t) { g = (0 until 9).map { k -> val r = k / 3; val cc = k % 3; g[(2 - cc) * 3 + r] } }
        return g
    }

    /** Feeds one picture at 6 Hz until the session confirms it (≤ 3 s). */
    private fun feed(s: ScanSession, fr: ScanFrame, t0: Long): Pair<ScanSession.Event.Confirmed?, Long> {
        var t = t0
        while (t < t0 + 3000) {
            val e = s.onGrid(GridFinder.find(fr, GridFinder.Roi.DEFAULT), t)
            if (e is ScanSession.Event.Confirmed) return e to t
            t += 160
        }
        return null to t
    }

    @Test
    fun gridFoundOnEverySyntheticPicture() {
        for (set in listOf("setA", "setB", "setC")) for (n in 1..6) {
            val g = GridFinder.find(frame(File(root, "$set/face$n.png")), GridFinder.Roi.DEFAULT)
            assertNotNull("$set face$n: no grid", g)
            // the 3 x 3 cells are evenly spaced: neighbour distance within half a cell of the pitch
            val c = g!!.cells
            for (r in 0 until 3) for (k in 0 until 2) {
                val a = c[r * 3 + k]
                val b = c[r * 3 + k + 1]
                val d = Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
                assertTrue("$set face$n row $r spacing $d vs pitch ${g.pitch}", Math.abs(d - g.pitch) < g.pitch / 2)
            }
        }
    }

    @Test
    fun firstReadAndAssembledAccuracy() {
        var right = 0
        var total = 0
        for (set in listOf("setA", "setB", "setC")) {
            val s = ScanSession(Lessons.readFaces)
            var t = 0L
            for (n in 1..6) {
                val (e, t2) = feed(s, frame(File(root, "$set/face$n.png")), t)
                assertNotNull("$set face$n not confirmed", e)
                val want = pictured(set, n)
                val got = e!!.read.colors
                for (k in 0 until 9) {
                    total++
                    if (want[k] == got[k]) right++
                }
                // the per-face confirm (user 2026-11-17): the user fixes the face, the colours are learnt
                s.confirmed(e.read.copy(colors = want.toList()))
                t = t2 + 1000
            }
            assertTrue(s.done)
            val res = CubeAssembler.assemble(s.reads)
            assertTrue("$set not legal", res.legal)
            assertEquals("$set assembled", truth(set).code(), res.cube.code())
            println("SCAN $set turns U=${res.turnU} D=${res.turnD} amber=${res.amber.size}")
        }
        val rate = right.toDouble() / total
        println("SCAN first-read $right/$total = ${"%.3f".format(rate)}")
        assertTrue("first read $rate < 0.95", rate >= 0.95)
    }

    @Test
    fun orientationFixEveryTurnOfUAndD() {
        val c = truth("setB")
        val labs = CubeColor.SIX.associateWith { ColorModel.DEFAULTS.getValue(it) }
        for (tu in 0 until 4) for (td in 0 until 4) {
            val reads = HashMap<Int, FaceRead>()
            for (f in 0 until 6) {
                var nine = (0 until 9).map { c[f * 9 + it] }
                val t = if (f == 0) tu else if (f == 3) td else 0
                repeat(t) { val g = nine; nine = (0 until 9).map { k -> g[(2 - k % 3) * 3 + k / 3] } }
                reads[f] = FaceRead(f, nine, nine.map { labs.getValue(it) }, List(9) { 1f })
            }
            val res = CubeAssembler.assemble(reads)
            assertTrue("U$tu D$td not fixed", res.legal)
            assertEquals("U$tu D$td", c.code(), res.cube.code())
        }
    }

    @Test
    fun jitterDoesNotConfirmEarly() {
        val fr = frame(File(root, "setB/face1.png"))
        val s = ScanSession(Lessons.readFaces)
        var t = 0L
        var confirmedAt = -1L
        for (i in 0 until 20) {
            val e = s.onGrid(GridFinder.find(shifted(fr, if (i % 2 == 0) 3 else -3, if (i % 3 == 0) 3 else 0, t)), t)
            if (e is ScanSession.Event.Confirmed) {
                confirmedAt = t
                break
            }
            t += 160
        }
        assertTrue("jitter: confirmed at $confirmedAt", confirmedAt >= Stabilizer.STABLE_MS)
    }

    @Test
    fun unstableColoursNeverConfirm() {
        // two different faces alternating every frame: never 0.6 s of the same nine colours
        val a = frame(File(root, "setB/face1.png"))
        val b = frame(File(root, "setB/face2.png"))
        val s = ScanSession(Lessons.readFaces)
        var t = 0L
        repeat(30) { i ->
            val e = s.onGrid(GridFinder.find(if (i % 2 == 0) a else b, GridFinder.Roi.DEFAULT), t)
            assertFalse("alternating faces confirmed at $t", e is ScanSession.Event.Confirmed)
            t += 160
        }
    }

    @Test
    fun sameFaceTwiceIsRefused() {
        val fr = frame(File(root, "setC/face1.png"))
        val s = ScanSession(Lessons.readFaces)
        val (e, t) = feed(s, fr, 0L)
        assertNotNull(e)
        var guard: Guard? = null
        var tt = t + 500
        repeat(10) {
            val ev = s.onGrid(GridFinder.find(fr, GridFinder.Roi.DEFAULT), tt)
            assertFalse("re-read face taken", ev is ScanSession.Event.Confirmed)
            guard = (ev as ScanSession.Event.Live).guard ?: guard
            tt += 160
        }
        assertEquals(Guard.REPEAT, guard)
        assertEquals(2, s.step)
    }

    @Test
    fun badSetFailsTheCheck() {
        val s = ScanSession(Lessons.readFaces)
        var t = 0L
        for (n in 1..6) {
            val (e, t2) = feed(s, frame(File(root, "setBad/face$n.png")), t)
            assertNotNull("setBad face$n", e)
            t = t2 + 1000
        }
        assertFalse(CubeAssembler.assemble(s.reads).legal)
    }

    @Test
    fun tierTimers() {
        val s = ScanSession(Lessons.readFaces)
        s.onGrid(null, 0L)
        assertEquals(1, (s.onGrid(null, 3900L) as ScanSession.Event.Live).tier)
        assertEquals(2, (s.onGrid(null, 4100L) as ScanSession.Event.Live).tier)
        assertEquals(3, (s.onGrid(null, 30100L) as ScanSession.Event.Live).tier)
    }

    @Test
    fun onlyInsideTheAlignmentFrame() {
        // the same face moved 250 px to the side (outside the box's ROI) is not read; the whole frame would read it
        val fr = shifted(frame(File(root, "setB/face2.png")), -250, 0, 0L)
        assertEquals(null, GridFinder.find(fr, GridFinder.Roi.DEFAULT))
    }

    @Test
    fun theFaceAskedForKeepsItsKnownCentre() {
        // user 2026-11-17: the side read is the side asked for; its centre is that side's colour, whatever the camera
        // made of the centre square
        val s = ScanSession(Lessons.readFaces)
        val asked = s.expectedFace
        val (e, _) = feed(s, frame(File(root, "setC/face1.png")), 0L)
        assertNotNull(e)
        assertEquals(asked, e!!.read.face)
        assertEquals(Cube.SCHEME.getValue(asked), e.read.colors[4])
        // the user says it was another side: it moves there (only to a side not read yet)
        val other = Lessons.readFaces.first { it != asked }
        assertTrue(s.moveTo(asked, other, e.read))
        assertTrue(s.reads.containsKey(other) && !s.reads.containsKey(asked))
    }

    @Test
    fun realHeadsetFramesWhenPresent() {
        val dir = listOf(File("src/test/resources/scan-real"), File("app/src/test/resources/scan-real")).firstOrNull { it.isDirectory } ?: return
        val pics = dir.listFiles { f -> f.name.endsWith(".png") }.orEmpty()
        val found = pics.count { GridFinder.find(frame(it), GridFinder.Roi.DEFAULT) != null }
        println("SCAN real frames: grid in $found / ${pics.size}")
    }
}
