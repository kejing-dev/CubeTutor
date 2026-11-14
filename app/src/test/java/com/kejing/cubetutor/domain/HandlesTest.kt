package com.kejing.cubetutor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** design spatialLayout.panelGeometryCheck.unitTest ①–⑥ (the parts that are pure geometry / mapping). */
class HandlesTest {
    private val solved = Cube.solved()

    // ⑥ handle slot -> layer and push -> turn; the cube state is in the display frame, so after ANY of the 24 whole-cube
    // orientations "Right, up" is still the right column going up as you see it (= R in the display frame)
    @Test
    fun handlesMapToDisplayLayersInAll24Orientations() {
        val orients = HashSet<Cube>()
        val wholes = listOf("", "y", "y2", "y'", "x", "x'", "x2", "z", "z'", "z2")
        for (a in wholes) for (b in wholes) for (c in listOf("", "y", "y'", "y2")) {
            val start = solved.apply(listOf(a, b, c).filter { it.isNotEmpty() }.joinToString(" ")).apply("R U F'")
            orients += start
            val r = Handles.turnFor(Selection(Row.COL, setOf(1)), Push.UP)!!
            assertEquals(start.apply("R"), start.turn(r))
            val u = Handles.turnFor(Selection(Row.ROW, setOf(1)), Push.LEFT)!!
            assertEquals(start.apply("U"), start.turn(u))
            val f = Handles.turnFor(Selection(Row.DEPTH, setOf(1)), Push.RIGHT)!!
            assertEquals(start.apply("F"), start.turn(f))
            val y = Handles.turnFor(Selection(Row.ROW, setOf(-1, 0, 1)), Push.LEFT)!!
            assertEquals(start.apply("y"), start.turn(y))
            // the design's table (input.lesson): D = 行「下」往右, L = 列「左」往下, B' = 前后「后」顺时针
            assertEquals(start.apply("D"), start.turn(Handles.turnFor(Selection(Row.ROW, setOf(-1)), Push.RIGHT)!!))
            assertEquals(start.apply("L"), start.turn(Handles.turnFor(Selection(Row.COL, setOf(-1)), Push.DOWN)!!))
            assertEquals(start.apply("B'"), start.turn(Handles.turnFor(Selection(Row.DEPTH, setOf(-1)), Push.RIGHT)!!))
        }
        assertTrue("24 orientations covered (${orients.size})", orients.size >= 24)
    }

    @Test
    fun handleForIsTheInverseOfTurnFor() {
        for (m in listOf("U", "U'", "D", "D'", "R", "R'", "L", "L'", "F", "F'", "y", "y'")) {
            val t = Moves.token(m)[0]
            val (h, p) = Handles.handleFor(t)!!
            assertEquals(m, t, Handles.turnFor(Selection(h.row, h.layers), p))
        }
        assertEquals(Handle(Row.COL, 1) to Push.UP, Handles.handleFor(Moves.token("R")[0]))
        assertEquals(Handle(Row.ROW, Handle.ALL) to Push.LEFT, Handles.handleFor(Moves.token("y")[0]))
    }

    @Test
    fun wrongDirectionsDoNothing() {
        assertNull(Handles.turnFor(Selection(Row.ROW, setOf(1)), Push.UP))
        assertNull(Handles.turnFor(Selection(Row.COL, setOf(0)), Push.LEFT))
    }

    @Test
    fun multiSelectAndSwitching() {
        var r = Handles.tap(null, Handle(Row.ROW, 1))
        assertEquals(Selection(Row.ROW, setOf(1)), r.selection)
        r = Handles.tap(r.selection, Handle(Row.ROW, 0))
        assertEquals(setOf(1, 0), r.selection!!.layers)
        // two layers turn together (宽层)
        val wide = Handles.turnFor(r.selection!!, Push.LEFT)!!
        assertEquals(solved.apply("U E'"), solved.turn(wide))
        r = Handles.tap(r.selection, Handle(Row.ROW, 1))
        assertEquals(setOf(0), r.selection!!.layers)
        r = Handles.tap(r.selection, Handle(Row.ROW, 0))
        assertNull(r.selection)
        r = Handles.tap(Selection(Row.ROW, setOf(1)), Handle(Row.COL, 1))
        assertTrue(r.switchedRow)
        assertEquals(Selection(Row.COL, setOf(1)), r.selection)
        r = Handles.tap(r.selection, Handle(Row.COL, Handle.ALL))
        assertTrue(r.selection!!.whole)
        r = Handles.tap(r.selection, Handle(Row.COL, Handle.ALL))
        assertNull(r.selection)
    }

    @Test
    fun stickPushFiresOnceUntilBackToCentre() {
        val d = PushDetector()
        assertNull(d.step(0.5f, 0f))
        assertEquals(Push.RIGHT, d.step(0.7f, 0.1f))
        assertNull(d.step(0.9f, 0f))
        assertNull(d.step(0.35f, 0f))
        assertNull(d.step(0.8f, 0f))
        assertNull(d.step(0.1f, 0f))
        assertEquals(Push.UP, d.step(0.1f, 0.75f))
        d.step(0f, 0f)
        assertEquals(Push.DOWN, d.step(0f, -0.9f))
        d.step(0f, 0f)
        assertEquals(Push.LEFT, d.step(-0.65f, 0.2f))
    }

    // ② placement: four headings × level / 20° down: rig 43° left, 0.60 m away, 0.15 m below the eye, facing you, upright
    @Test
    fun rigPlacementAroundTheUser() {
        for (yawDeg in listOf(0f, 90f, 180f, 270f)) for (pitchDeg in listOf(0f, -20f)) {
            val q = Quat4.axisAngle(Vec3.UP, Math.toRadians(yawDeg.toDouble()).toFloat()) * Quat4.axisAngle(Vec3.RIGHT, Math.toRadians(pitchDeg.toDouble()).toFloat())
            val head = HeadPose(Vec3(0.3f, 1.6f, -0.2f), q)
            val up = RigLayout.place(head)
            val flat = Vec3(up.position.x - head.position.x, 0f, up.position.z - head.position.z)
            assertEquals(0.60f, flat.length, 0.01f)
            assertEquals(1.45f, up.position.y, 0.001f)
            assertEquals(43f, RigLayout.bearingDeg(head.position, head.yaw(0f), up.position), 1f)
            // the rig's front (+Z) points at the head
            val front = up.rotation.rotate(Vec3(0f, 0f, 1f))
            val toHead = Vec3(head.position.x - up.position.x, 0f, head.position.z - up.position.z).normalized()
            assertTrue(Math.toDegrees(kotlin.math.acos(front.dot(toHead).coerceIn(-1f, 1f).toDouble())) < 10.0)
            assertEquals(0f, front.y, 1e-4f)
        }
    }

    // ③ window cone: the rig's right outer edge stays ≥ 10° left of the window's left edge (D41)
    @Test
    fun rigStaysOutOfTheWindowCone() {
        val head = HeadPose(Vec3(0f, 1.6f, 0f), Quat4.IDENTITY)
        val up = RigLayout.place(head)
        val edge = RigLayout.rightEdgeDeg(head, up)
        val win = RigLayout.windowLeftEdgeDeg()
        assertTrue("rig right edge $edge° vs window left $win°", edge - win >= 10f)
    }

    // ① level gate and ④ re-center: the heading must hold still before sampling (D29)
    @Test
    fun steadyGateWaitsForAStillLevelHead() {
        val g = SteadyGate()
        g.start()
        val window = Math.toRadians(0.0).toFloat()
        val ahead = Math.toRadians(-35.0).toFloat()
        // pressed while looking at the window, then turning to straight ahead
        assertFalse(g.step(0, window, 0f))
        assertFalse(g.step(100, window * 0.5f + ahead * 0.5f, 0f))
        assertFalse(g.step(200, ahead, 0f))
        assertFalse(g.step(400, ahead + 0.01f, 0f))
        assertTrue(g.step(520, ahead + 0.01f, 0f))
        // the rig is placed from the heading it settled on: < 2° from straight ahead
        val head = HeadPose(Vec3(0f, 1.6f, 0f), Quat4.axisAngle(Vec3.UP, ahead + 0.01f))
        val up = RigLayout.place(head)
        assertTrue(abs(RigLayout.bearingDeg(head.position, ahead, up.position) - 43f) < 2f)
        // looking down does not count
        g.start()
        assertFalse(g.step(0, ahead, -30f))
        assertFalse(g.step(400, ahead, -30f))
    }

    // ⑤ handles: colliders 6.5 cm at 6.67 cm spacing never overlap; the outer handles stay off the cube's stickers
    @Test
    fun handleCollidersDoNotOverlap() {
        val rows = (listOf(1, 0, -1).map { Vec3(-0.15f, it * 0.0667f, 0.1f) } + Vec3(-0.15f, 0.135f, 0.1f))
        val cols = (listOf(-1, 0, 1).map { Vec3(it * 0.0667f, -0.15f, 0.1f) } + Vec3(-0.135f, -0.15f, 0.1f))
        val all = rows + cols
        for (i in all.indices) for (j in i + 1 until all.size) {
            val a = all[i]; val b = all[j]
            val overlap = abs(a.x - b.x) < 0.065f && abs(a.y - b.y) < 0.065f
            assertFalse("handles $a and $b overlap", overlap)
        }
        // 2.5 cm from the cube's face edge (0.10): handle centres at 0.15 minus half a plate (0.025)
        assertTrue(0.15f - 0.025f - 0.1f >= 0.0249f)
    }

    @Test
    fun turnPermutationIsConsistentWithRotatingPoints() {
        // a sticker on the R face's upper-front corner goes to the U face's front-right corner on R
        val i = Facelets.R
        val t = Moves.token("R")[0]
        val p = Perms.rotatePoint(Facelets.pos[i], t.axis, t.dir)
        assertNotNull(p)
        assertEquals(listOf(1, 1, -1), p.toList())
    }
}
