package com.kejing.cubetutor.platform.input

import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.controller.ControllerActionData
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.kejing.cubetutor.domain.Quat4
import com.kejing.cubetutor.domain.Vec3
import com.kejing.cubetutor.platform.CtLog
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** A pointing ray in Stage metres. */
data class Ray(val origin: Vec3, val dir: Vec3)

/**
 * Both PICO controllers in Full Space (design input.joystick, container.mrCapabilities; SDK 6.1 controller-tracking is
 * Full Space only): the thumbstick of the controller you used last (either hand), trigger / A = tap / hold the tray,
 * B (or Y) = clear the pick. The listener runs on the data source's thread and only copies values into atomics; the
 * frame system reads them. The ray comes from the aiming controller's pose (-Z forward).
 */
class Controllers {
    private var provider: ControllerTrackingProvider? = null
    private val sx = AtomicInteger(0)
    private val sy = AtomicInteger(0)
    private val trigR = AtomicBoolean(false)
    private val trigL = AtomicBoolean(false)
    private val bDown = AtomicBoolean(false)
    private val bPresses = AtomicInteger(0)
    @Volatile private var useRight = true
    private val actions = AtomicInteger(0)
    private var lastSupport: DataProvider.SupportState? = null

    private val listener =
        ControllerTrackingProvider.ControllerActionListener { a: ControllerActionData ->
            actions.incrementAndGet()
            val r = runCatching { a.right.triggerPressed || a.right.aButtonPressed }.getOrDefault(false)
            val l = runCatching { a.left.triggerPressed || a.left.xButtonPressed }.getOrDefault(false)
            if (l && !trigL.get()) useRight = false
            if (r && !trigR.get()) useRight = true
            trigR.set(r)
            trigL.set(l)
            // either hand's stick: the one pushed further (design: 哪只手柄都行)
            val rx = runCatching { a.right.thumbstickValue.x }.getOrDefault(0f)
            val ry = runCatching { a.right.thumbstickValue.y }.getOrDefault(0f)
            val lx = runCatching { a.left.thumbstickValue.x }.getOrDefault(0f)
            val ly = runCatching { a.left.thumbstickValue.y }.getOrDefault(0f)
            val (x, y) = if (rx * rx + ry * ry >= lx * lx + ly * ly) rx to ry else lx to ly
            sx.set(bits(x))
            sy.set(bits(y))
            val b = runCatching { a.right.bButtonPressed || a.left.yButtonPressed }.getOrDefault(false)
            if (b && !bDown.getAndSet(true)) bPresses.incrementAndGet()
            if (!b) bDown.set(false)
        }

    private fun bits(v: Float) = java.lang.Float.floatToIntBits(if (v.isFinite()) v else 0f)

    private fun f(a: AtomicInteger) = java.lang.Float.intBitsToFloat(a.get())

    fun start() {
        if (provider != null) return
        val p = ControllerTrackingProvider()
        runCatching { p.addControllerActionListener(listener) }
        val r = runCatching { p.start() }
        lastSupport = runCatching { p.supportState }.getOrNull()
        CtLog.i("TRACK controller start=${r.getOrNull() ?: r.exceptionOrNull()} support=$lastSupport")
        provider = p
    }

    fun stop() {
        provider?.let { p ->
            runCatching { p.removeControllerActionListener(listener) }
            runCatching { p.stop() }
        }
        provider = null
        sx.set(0)
        sy.set(0)
        trigR.set(false)
        trigL.set(false)
    }

    fun supported(): Boolean {
        val s = provider?.let { runCatching { it.supportState }.getOrNull() }
        if (s != lastSupport) {
            lastSupport = s
            CtLog.i("TRACK controller support=$s actions=${actions.get()}")
        }
        return s == DataProvider.SupportState.SUPPORTED
    }

    /** Stick (x + right, y + up). */
    fun stick(): Pair<Float, Float> = f(sx) to f(sy)

    /** Trigger (or A / X) held on the aiming controller. */
    fun triggerHeld(): Boolean = if (useRight) trigR.get() else trigL.get()

    fun takeBPresses(): Int = bPresses.getAndSet(0)

    /** Ray of the aiming controller (the one whose trigger was used last), or null without a pose. */
    fun ray(): Ray? {
        val p = provider ?: return null
        if (!supported()) return null
        val data = runCatching { p.latestData }.getOrNull() ?: return null
        val pose = (if (useRight) data.right ?: data.left else data.left ?: data.right) ?: return null
        val pos = Vec3(pose.position.x, pose.position.y, pose.position.z)
        val q = Quat4(pose.rotation.x, pose.rotation.y, pose.rotation.z, pose.rotation.w)
        if (!pos.isFinite()) return null
        val fwd = q.rotate(Vec3.AHEAD)
        if (!fwd.isFinite() || fwd.length < 0.5f) return null
        return Ray(pos, fwd.normalized())
    }
}
