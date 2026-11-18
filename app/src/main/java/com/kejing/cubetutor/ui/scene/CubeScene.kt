package com.kejing.cubetutor.ui.scene

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.simulation.CollisionCastHitMode
import com.pico.spatial.core.ecs.simulation.CollisionGroup
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.kejing.cubetutor.domain.Axis
import com.kejing.cubetutor.domain.Cube
import com.kejing.cubetutor.domain.CubeColor
import com.kejing.cubetutor.domain.Facelets
import com.kejing.cubetutor.domain.Handle
import com.kejing.cubetutor.domain.Handles
import com.kejing.cubetutor.domain.HeadPose
import com.kejing.cubetutor.domain.Lessons
import com.kejing.cubetutor.domain.ReadPose
import com.kejing.cubetutor.domain.TapDeduper
import com.kejing.cubetutor.ui.ReadMode
import com.kejing.cubetutor.domain.Moves
import com.kejing.cubetutor.domain.Push
import com.kejing.cubetutor.domain.PushDetector
import com.kejing.cubetutor.domain.Quat4
import com.kejing.cubetutor.domain.RigLayout
import com.kejing.cubetutor.domain.Row
import com.kejing.cubetutor.domain.Turn
import com.kejing.cubetutor.domain.Upright
import com.kejing.cubetutor.domain.Vec3
import com.kejing.cubetutor.domain.Yaw
import com.kejing.cubetutor.domain.placeUpright
import com.kejing.cubetutor.platform.CtLog
import com.kejing.cubetutor.platform.input.Controllers
import com.kejing.cubetutor.platform.tracking.HeadTracker
import com.kejing.cubetutor.ui.Anim
import com.kejing.cubetutor.ui.CtController
import com.kejing.cubetutor.ui.CtUiState
import com.kejing.cubetutor.ui.HintKind
import com.kejing.cubetutor.ui.Lang
import com.kejing.cubetutor.ui.Tab
import com.kejing.cubetutor.ui.Ui
import com.kejing.cubetutor.ui.components.localizedResources
import com.kejing.cubetutor.ui.input.CubeNames
import com.kejing.cubetutor.ui.input.CubeTarget
import com.kejing.cubetutor.ui.input.resolveUp
import com.kejing.cubetutor.ui.input.TargetResolver
import com.kejing.cubetutor.ui.theme.CtColors
import com.kejing.cubetutor.ui.window.WindowCtl
import com.kejing.cubetutor.R
import java.util.Locale
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The scene of the open Stage (debug hooks and the frame system talk to it). Main thread only. */
internal object ActiveScene {
    var current: CubeScene? = null
}

/** The one per-frame ECS system of the app (`registerSystem` in CtScreen). */
class CubeFrameSystem : System() {
    override fun update(context: SceneUpdateContext) {
        ActiveScene.current?.onFrame(context)
    }
}

object PanelIds {
    /** "打开面板": shown only while the window is closed / minimised (native-window-move §3.3), above the rig. */
    const val CHIP = "chip"

    /** "准备中 / 平视前方 / 看正前方": one line on the line of sight while the window waits (S00 / S28 / S30). */
    const val HINT = "hint"

    /** The scan card (design readFlow.scanCard): the only panel near your gaze while the camera reads (S02 / S03). */
    const val SCAN = "scan"
    val pool = listOf(CHIP, HINT, SCAN)
}

/**
 * The cube rig on the Stage (design entities3d, spatialLayout): rig (yaw only) -> presentRoot (fixed 3/4 view) ->
 * cubeRoot -> 26 rounded cubies with their stickers; three rows of handles, the bar with two arrows, the tray; the
 * app-drawn controller ray. All per-frame work is here ([onFrame]): head + controllers, turn animations from
 * [CtController.anims], materials from [CtController.state]. Nothing per-frame goes through Compose.
 */
class CubeScene {
    val panelShown = mutableStateMapOf<String, Boolean>()
    val bindTick = mutableIntStateOf(0)

    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private val head = HeadTracker()
    private val controllers = Controllers()
    private val pushes = PushDetector()
    private var content: SpatialViewContent? = null
    private var contentProbe: Entity? = null
    private var stageRoot: Entity? = null
    private var panels: PanelRig? = null
    private var shared: SharedResources? = null
    private var appContext: Context? = null
    private var buildJob: Job? = null
    private var built = false
    private var attached = false
    private var paused = false

    // ---- entities
    private var rig: Entity? = null
    private var present: Entity? = null
    private var cubeRoot: Entity? = null
    private val cubies = HashMap<Int, Entity>() // key = cubie position code
    private val cubieHome = HashMap<Int, Vec3>()

    /** Reading pose 0 (normal) .. 1 (loosened, face-on); eased over [EXPLODE_MS]. */
    private var explode = 0f
    private var explodeWritten = -1f
    private var lastExplodeMs = 0L

    /** Which stickers have their collider on (reading: only the active face, design readPose.activeFace). */
    private val stickerHit = BooleanArray(54)
    private val stickers = arrayOfNulls<ModelEntity>(54)
    private val stickerMat = arrayOfNulls<Material>(54)
    private val handlePlates = HashMap<Handle, Entity>()
    private val handleLabels = HashMap<Handle, ModelEntity>()
    private val handleMat = HashMap<Handle, Material>()
    private var dpadBar: Entity? = null
    private var barLabel: ModelEntity? = null
    private var barText = ""
    private val arrowEnt = arrayOfNulls<Entity>(2)
    private val arrowLabel = arrayOfNulls<ModelEntity>(2)
    private val arrowMat = arrayOfNulls<Material>(2)
    private var tray: Entity? = null
    private var trayLabel: ModelEntity? = null
    private val ghosts = ArrayList<ModelEntity>()
    private var outline: ModelEntity? = null
    private var outlineMesh: MeshResource? = null
    private var outlineKey = ""
    private var beam: ModelEntity? = null
    private var dot: ModelEntity? = null
    private var kit: Kit? = null

    // ---- head and rig
    private var pose = HeadPose(Vec3(0f, 1.6f, 0f), Quat4.IDENTITY)
    private var hasHead = false
    private var startMs = 0L
    private var rigPose: Upright? = null
    private var placeEpoch = 0
    private var dragStart: Upright? = null
    private var dragHandStart: Vec3? = null
    private var lastAwayCheck = 0L

    // ---- the cube on screen
    var displayed: Cube = Cube.solved()
        private set
    private var spin: Anim.Spin? = null
    private var pauseUntil = 0L
    private var spinStart = 0L
    private var lastTurn: Turn? = null
    private var lastTurnAt = 0L
    private var readRot: Quat4 = Quat4.IDENTITY
    private var homeSpin = 0f
    private var celebrateAt = 0L
    private var wasSolved = false
    private var relevant: Set<Int>? = null
    private var relevantFor = -1

    // ---- ray
    private var rayDown = false
    private var rayTarget: String? = null

    // ---- logs
    private var frames = 0L
    private var lastFrameMs = 0L
    private var startupLogs = 0
    private var lastErrorMs = 0L

    /** Debug `freeze=1`: hold the turn animation where it is (QA screenshot of S25 mid-turn). */
    var frozen = false
    var debugYawDeg = 0f

    // =========================================================================================== lifecycle

    fun attach(content: SpatialViewContent, attachments: SpatialViewAttachments, context: Context) {
        if (attached || stageRoot != null) detach()
        appContext = context.applicationContext
        startMs = SystemClock.elapsedRealtime()
        this.content = content
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val root = Entity().named("stageRoot")
        content.addEntity(root)
        val probe = Entity().named("contentProbe")
        content.addEntity(probe)
        contentProbe = probe
        // an entity added to the SpatialView is not at the Stage origin (Spatial Ruler): move the root once
        val offset = root.convertPositionFrom(Vector3.ZERO, null)
        val rotation = root.convertRotationFrom(Quat.identity(), null)
        root.components[TransformComponent::class.java]?.apply {
            setPosition(offset)
            setQuaternion(rotation)
        }
        stageRoot = root
        val pr = PanelRig(root) { id, shown -> panelShown[id] = shown }
        panels = pr
        pr.world(PanelIds.CHIP, CHIP_W, CHIP_H, PANEL_SCALE).maxPitch = 0.45f
        pr.world(PanelIds.HINT, HINT_W, HINT_H, PANEL_SCALE).maxPitch = 0f
        // the alignment frame (user addendum 2026-11-11): head-locked, ~0.33 m ahead, a little below the eyes
        // placed every frame from the head pose (world panel, same metre scale as the chip). Emulator check 2026-11-11:
        // box ≈ 11.6° wide, centre ≈ 9.7° below the line of sight in a ~90° single-eye screenshot (designed 9.9° / 8°)
        pr.world(PanelIds.SCAN, SCAN_W, SCAN_H, FRAME_K).maxPitch = 1.4f
        head.start()
        controllers.start()
        ActiveScene.current = this
        attached = true
        CtController.quitHook = { releaseAll() }
        bindPanels(attachments)
        buildJob = scope?.launch { build(root) }
        CtLog.i("SCENE attached root offset=${CtLog.pos(offset.domain())}")
    }

    /** Meshes, materials and label textures on the IO thread (ANR rule); entities on main, a frame apart. */
    private suspend fun build(root: Entity) {
        delay(120)
        val t0 = SystemClock.elapsedRealtime()
        val sh = SharedResources()
        shared = sh
        val k = withContext(Dispatchers.IO) { Kit(sh) }
        kit = k
        // light: PBR plastic + the system's ambient + one warm key light (design theme.lighting, B24: not blown out)
        val sun = Entity().named("sun")
        sun.components.set(DirectionalLightComponent(Color4(1f, 0.96f, 0.9f, 1f), SUN_LUX, false))
        root.addChild(sun)
        sun.setLocal(rotation = Quat4.fromTo(Vec3(0f, 0f, -1f), Vec3(0.3f, -0.75f, -0.6f).normalized()))
        val r = Entity().named("cubeRig")
        root.addChild(r)
        r.enabled = false
        rig = r
        val p = Entity().named("presentRoot")
        r.addChild(p)
        p.setLocal(rotation = PRESENT)
        present = p
        val c = Entity().named("cubeRoot")
        p.addChild(c)
        cubeRoot = c
        delay(16)
        buildCubies(c, k)
        delay(16)
        buildHandles(r, p, k)
        delay(16)
        buildBar(r, k)
        buildGhosts(c, k)
        val o = model(k.emptyOutline().also { outlineMesh = it }, k.outlineWhite, "outline", c)
        o.enabled = false
        outline = o
        beam = model(k.block, k.rayWhite, "rayBeam", root).also { it.enabled = false }
        dot = model(k.disc, k.rayWhite, "rayDot", root).also { it.enabled = false }
        built = true
        displayed = CtController.state.value.cube
        recolorAll(CtController.state.value, SystemClock.elapsedRealtime(), force = true)
        CtLog.i("SCENE built ms=${SystemClock.elapsedRealtime() - t0} cubies=${cubies.size} stickers=${stickers.count { it != null }} handles=${handlePlates.size}")
        logInstances()
    }

    private fun collider(e: Entity, size: Vec3, hover: Boolean = true) {
        e.components.set(CollisionComponent(collisionShape = listOf(ShapeResource.createBox(Vector3(size.x, size.y, size.z))), physicsMaterial = PhysicsMaterialResource()))
        e.components.set(InteractableComponent())
        if (hover) runCatching { e.components.set(HoverEffectComponent()) }
    }

    private fun buildCubies(parent: Entity, k: Kit) {
        for (x in -1..1) for (y in -1..1) for (z in -1..1) {
            if (x == 0 && y == 0 && z == 0) continue
            val key = code(x, y, z)
            val home = Vec3(x * SP, y * SP, z * SP)
            val e = Entity().named("cubie:$x,$y,$z")
            parent.addChild(e)
            e.setLocal(position = home)
            model(k.cubieMesh, k.body, "body", e)
            cubies[key] = e
            cubieHome[key] = home
        }
        for (i in 0 until 54) {
            val pp = Facelets.pos[i]
            val n = Facelets.normal[i]
            val nv = Vec3(n[0].toFloat(), n[1].toFloat(), n[2].toFloat())
            val cube = cubies.getValue(code(pp[0], pp[1], pp[2]))
            // visible tile = the hit target (collider + HoverEffect on the mesh itself, board-input README)
            val st = ModelEntity(k.tile, k.sticker(CubeColor.NONE, false))
            st.named(CubeNames.sticker(i))
            cube.addChild(st)
            st.setLocal(position = nv * (HALF + 0.0007f), rotation = Quat4.fromTo(Vec3(0f, 0f, 1f), nv))
            // design readPose.sticker: collider = visible tile 5.5 x 5.5 cm, no padding (QA #1 q1-3)
            collider(st, Vec3(TILE, TILE, 0.006f))
            stickers[i] = st
            stickerHit[i] = true
        }
    }

    private fun buildHandles(r: Entity, p: Entity, k: Kit) {
        for (h in Handle.every) {
            val (parent, pos) =
                when (h.row) {
                    Row.ROW -> p to Vec3(-0.15f, if (h.slot == Handle.ALL) 0.135f else h.slot * 0.0667f, 0.10f)
                    Row.COL -> p to Vec3(if (h.slot == Handle.ALL) -0.135f else h.slot * 0.0667f, -0.15f, 0.10f)
                    Row.DEPTH -> r to Vec3(0.225f, if (h.slot == Handle.ALL) -0.135f else h.slot * 0.0667f, 0.033f)
                }
            val holder = Entity().named(CubeNames.handle(h))
            parent.addChild(holder)
            holder.setLocal(position = pos)
            val plate = model(k.handleSlab, k.handleBack, CubeNames.label("plate"), holder)
            plate.setLocal(position = Vec3.ZERO)
            val label = model(k.handleFace, k.blank, CubeNames.label(CubeNames.handle(h)), holder)
            label.setLocal(position = Vec3(0f, 0f, 0.0032f))
            // = the visible 5 cm plate (q1-3 same rule): neighbours 6.67 cm apart leave 1.67 cm between colliders
            collider(holder, Vec3(HANDLE_HIT, HANDLE_HIT, 0.02f), hover = false)
            runCatching { label.components.set(HoverEffectComponent()) }
            handlePlates[h] = holder
            handleLabels[h] = label
        }
    }

    private fun buildBar(r: Entity, k: Kit) {
        val bar = Entity().named("dpadBar")
        r.addChild(bar)
        dpadBar = bar
        bar.setLocal(position = Vec3(0f, -0.245f, 0.12f))
        model(k.barSlab, k.handleBack, "barPlate", bar)
        barLabel = model(k.barFace, k.blank, "barLabel", bar).also { it.setLocal(position = Vec3(0f, 0f, 0.0032f)) }
        for (i in 0..1) {
            val a = Entity().named(CubeNames.arrow(i == 0))
            bar.addChild(a)
            a.setLocal(position = Vec3(if (i == 0) -0.105f else 0.105f, 0f, 0.006f))
            model(k.arrowSlab, k.handleBack, CubeNames.label("arrowPlate"), a)
            arrowLabel[i] = model(k.arrowFace, k.blank, CubeNames.label(CubeNames.arrow(i == 0)), a).also {
                it.setLocal(position = Vec3(0f, 0f, 0.0032f))
                runCatching { it.components.set(HoverEffectComponent()) }
            }
            collider(a, Vec3(0.08f, 0.08f, 0.02f), hover = false)
            arrowEnt[i] = a
        }
        val t = Entity().named(CubeNames.TRAY)
        r.addChild(t)
        t.setLocal(position = Vec3(0f, -0.335f, 0.11f)) // design -0.305: moved 3 cm down, the bar (y -0.205…-0.285) covered its top edge
        model(k.traySlab, k.trayBack, CubeNames.label("trayPlate"), t)
        trayLabel = model(k.trayFace, k.blank, CubeNames.label(CubeNames.TRAY), t).also {
            it.setLocal(position = Vec3(0f, 0f, 0.0032f))
            runCatching { it.components.set(HoverEffectComponent()) }
        }
        collider(t, Vec3(0.32f, 0.07f, 0.03f), hover = false)
        tray = t
    }

    private fun buildGhosts(parent: Entity, k: Kit) {
        repeat(9) { i -> ghosts += model(k.cubieMesh, k.ghost, "ghost:$i", parent).also { it.enabled = false } }
    }

    fun pause() {
        if (paused || !attached) return
        paused = true
        head.stop()
        controllers.stop()
        CtLog.i("SCENE paused")
    }

    fun resume() {
        if (!paused || !attached) return
        paused = false
        head.start()
        controllers.start()
        lastAwayCheck = 0L
        CtLog.i("SCENE resumed")
    }

    fun bindPanels(attachments: SpatialViewAttachments) {
        val pr = panels ?: return
        if (!pr.bind(attachments, PanelIds.pool, SystemClock.elapsedRealtime())) {
            main.removeCallbacks(rebind)
            main.postDelayed(rebind, 250L)
        }
    }

    private val rebind = Runnable { if (stageRoot != null) bindTick.intValue++ }

    private fun releaseAll() {
        head.stop()
        controllers.stop()
    }

    fun detach() {
        if (!attached && stageRoot == null) return
        attached = false
        if (ActiveScene.current === this) ActiveScene.current = null
        CtController.quitHook = null
        CtController.animating = false
        main.removeCallbacksAndMessages(null)
        buildJob?.cancel()
        scope?.cancel()
        scope = null
        releaseAll()
        panelShown.clear()
        runCatching { panels?.release() }
        runCatching { stageRoot?.destroy(true) }
        runCatching { contentProbe?.destroy(true) }
        shared?.close()
        shared = null
        kit = null
        stageRoot = null
        panels = null
        cubies.clear()
        handlePlates.clear()
        handleLabels.clear()
        handleMat.clear()
        ghosts.clear()
        built = false
        CtLog.i("SCENE detached")
    }

    // =========================================================================================== per frame

    fun onFrame(context: SceneUpdateContext) {
        val root = stageRoot ?: return
        if (root.scene == null || context.scene != root.scene) return
        val now = SystemClock.elapsedRealtime()
        lastFrameMs = now
        frames++
        head.pose()?.let {
            // a first frame of all zeros + identity is "not tracking yet" (hand-sign teacher lesson)
            val zero = it.position.length < 1e-3f && abs(it.rotation.w) > 0.99999f
            if (!zero) {
                pose = it
                hasHead = true
            }
        }
        val pr = panels ?: return
        pr.onFrame(now)
        pr.stowBehind(pose)
        val pitch = if (hasHead) Math.toDegrees(pose.pitch.toDouble()).toFloat() else null
        WindowCtl.stepGate(now, pitch, pose.yaw(0f))
        if (hasHead || now - startMs > NO_HEAD_MS) CtController.onReady()
        CtController.onWindow(WindowCtl.open, WindowCtl.levelHint, WindowCtl.recentering)
        if (!attached || paused) return
        val ui = CtController.state.value
        try {
            if (built) {
                stepPlacement(ui, now)
                stepAnim(ui, now)
                stepCubeRoot(ui, now)
                recolorAll(ui, now)
                stepHandles(ui, now)
                stepGhost(ui, now)
                stepOutline(ui, now)
                stepRay(ui, now)
            }
            placePanels(ui, now)
        } catch (t: Throwable) {
            // an engine call failing for one frame (runtime restarting) must not take the app down
            if (now - lastErrorMs > 2000) {
                lastErrorMs = now
                CtLog.e("FRAME step failed: $t", t)
            }
        }
        pr.faceViewer(pose.position)
        stepLogs(ui, now)
    }

    // ---- rig placement (design rigPlacement, M8, D9 / D29)

    private fun placeHead(): HeadPose =
        if (debugYawDeg == 0f) pose else HeadPose(pose.position, Quat4.axisAngle(Vec3.UP, Math.toRadians(debugYawDeg.toDouble()).toFloat()) * pose.rotation)

    private fun stepPlacement(ui: CtUiState, now: Long) {
        val r = rig ?: return
        if (WindowCtl.placeEpoch != placeEpoch && (hasHead || now - startMs > NO_HEAD_MS)) {
            placeEpoch = WindowCtl.placeEpoch
            val hp = placeHead()
            val up = RigLayout.place(hp)
            setRig(up)
            r.on(true)
            val edge = RigLayout.rightEdgeDeg(hp, up)
            val win = RigLayout.windowLeftEdgeDeg()
            CtLog.i(String.format(Locale.US, "PLACE rig=%s yaw=%.1f head=%s headYaw=%.1f pitch=%.1f rightEdge=%.1f° windowLeft=%.1f° gap=%.1f° nohead=%s", CtLog.pos(up.position), Math.toDegrees(up.yaw.toDouble()), CtLog.pos(hp.position), Math.toDegrees(hp.yaw(0f).toDouble()), Math.toDegrees(hp.pitch.toDouble()), edge, win, edge - win, !hasHead))
        }
        if (rigPose == null && WindowCtl.placeEpoch == 0 && now - startMs > NO_HEAD_MS * 3) {
            // no window ever opened (e.g. the system refused): still show the cube where it belongs
            setRig(RigLayout.place(placeHead()))
            r.on(true)
            CtLog.w("PLACE fallback without a window open")
        }
        // a cube far from where you look (back from the background, design lifecycle): say so, do not move it
        val rp = rigPose
        if (rp != null && hasHead && now - lastAwayCheck > 1000 && !ui.dragging) {
            lastAwayCheck = now
            CtController.onAway(RigLayout.misplaced(pose, rp.position))
        }
    }

    private fun setRig(up: Upright) {
        rigPose = up
        rig?.setLocal(position = up.position, rotation = up.rotation)
    }

    // ---- turn animations (design cubeSpec.turnAnim: 0.25 s ease-in-out, input locked)

    private fun readMode(ui: CtUiState) = ui.read != null && ui.tab == Tab.HOME

    private fun stepAnim(ui: CtUiState, now: Long) {
        stepReadPose(ui, now)
        if (readMode(ui)) {
            // reading: the tapped cube as it is, no animations
            if (spin != null) finishSpin(now)
            CtController.anims.clear()
            CtController.animating = false
            displayed = CtController.readCube
            return
        }
        if (spin == null && now < pauseUntil) {
            CtController.animating = true
            return
        }
        if (spin == null) {
            while (true) {
                val a = CtController.anims.removeFirstOrNull() ?: break
                when (a) {
                    is Anim.Pause -> {
                        pauseUntil = now + a.ms
                        CtController.animating = true
                        return
                    }
                    is Anim.Snap -> {
                        displayed = a.cube
                        CtLog.i("ANIM snap ${a.why}")
                    }
                    is Anim.Spin -> {
                        spin = a
                        spinStart = now
                        break
                    }
                }
            }
        }
        val s = spin
        if (s == null) {
            CtController.animating = false
            // nothing queued: the screen must show the logical cube (except the "why" demo)
            if (ui.why == null && displayed != ui.cube) {
                CtLog.i("ANIM resync to the logical cube")
                displayed = ui.cube
            }
            return
        }
        CtController.animating = true
        if (frozen) return
        val t = ((now - spinStart).toFloat() / s.ms).coerceIn(0f, 1f)
        if (t >= 1f) {
            finishSpin(now)
            return
        }
        val e = (0.5f - 0.5f * cos(PI.toFloat() * t))
        val q = turnRot(s.turn, e)
        for ((key, ent) in cubies) {
            val home = cubieHome.getValue(key)
            if (inLayer(key, s.turn)) ent.setLocal(position = q.rotate(home), rotation = q)
        }
    }

    /**
     * Design readPose (QA #1 q1-3): in S02–S07 the cube loosens (pitch 6.73 → 7.5 cm, cubies unchanged), presentRoot
     * goes from the 3/4 view to face-on (tilted 14° to your eyes), and only the active face's 9 stickers can be hit;
     * back to normal in S08 / elsewhere. 0.35 s ease-in-out both ways.
     */
    private fun stepReadPose(ui: CtUiState, now: Long) {
        val want = if (readMode(ui)) 1f else 0f
        val dt = if (lastExplodeMs == 0L) 0L else (now - lastExplodeMs).coerceIn(0L, 100L)
        lastExplodeMs = now
        explode = if (want > explode) minOf(want, explode + dt / EXPLODE_MS) else maxOf(want, explode - dt / EXPLODE_MS)
        if (explode != explodeWritten && spin == null) {
            explodeWritten = explode
            val e = 0.5f - 0.5f * cos(PI.toFloat() * explode)
            val k = 1f + (SP_READ / SP - 1f) * e
            for ((key, ent) in cubies) ent.setLocal(position = cubieHome.getValue(key) * k, rotation = Quat4.IDENTITY)
            present?.setLocal(rotation = slerp(PRESENT, FACE_ON, e))
        }
        // colliders: reading = only the active face; otherwise every sticker
        val active = if (readMode(ui)) ui.read!!.target else -1
        for (i in 0 until 54) {
            val on = ReadPose.hittable(if (active < 0) null else active, i)
            if (on == stickerHit[i]) continue
            val st = stickers[i] ?: continue
            stickerHit[i] = on
            if (on) collider(st, Vec3(TILE, TILE, 0.006f))
            else {
                runCatching { st.components.remove(InteractableComponent::class.java) }
                runCatching { st.components.remove(CollisionComponent::class.java) }
                runCatching { st.components.remove(HoverEffectComponent::class.java) }
            }
        }
    }

    private fun finishSpin(now: Long) {
        val s = spin ?: return
        for ((key, ent) in cubies) if (inLayer(key, s.turn)) ent.setLocal(position = cubieHome.getValue(key), rotation = Quat4.IDENTITY)
        displayed = displayed.turn(s.turn)
        lastTurn = s.turn
        lastTurnAt = now
        spin = null
        CtController.animating = CtController.anims.isNotEmpty()
    }

    private fun turnRot(t: Turn, frac: Float): Quat4 {
        val axis = when (t.axis) { Axis.X -> Vec3(1f, 0f, 0f); Axis.Y -> Vec3(0f, 1f, 0f); Axis.Z -> Vec3(0f, 0f, 1f) }
        return Quat4.axisAngle(axis, t.dir * (PI.toFloat() / 2f) * frac)
    }

    private fun inLayer(key: Int, t: Turn): Boolean {
        val c = decode(key)
        return c[t.axis.v] in t.layers
    }

    // ---- cubeRoot: reading turns the face being read to you; home spins a solved cube; solved celebrates

    private fun stepCubeRoot(ui: CtUiState, now: Long) {
        val c = cubeRoot ?: return
        val solved = (ui.lesson?.solved == true) || ui.play.solved
        if (solved && !wasSolved) celebrateAt = now
        wasSolved = solved
        val target: Quat4 =
            when {
                readMode(ui) -> READ_ROT[Lessons.readFaces.indexOf(ui.read!!.target).coerceIn(0, 5)]
                now - celebrateAt < CELEBRATE_MS -> Quat4.axisAngle(Vec3.UP, 2f * PI.toFloat() * ((now - celebrateAt).toFloat() / CELEBRATE_MS))
                ui.tab == Tab.HOME && !ui.ready && ui.lesson == null && ui.selection == null && displayed.isSolved && spin == null -> {
                    homeSpin = (homeSpin + Math.toRadians(6.0).toFloat() * ((now - lastSpinMs).coerceIn(0, 200) / 1000f))
                    Quat4.axisAngle(Vec3.UP, homeSpin)
                }
                else -> {
                    homeSpin = 0f
                    Quat4.IDENTITY
                }
            }
        lastSpinMs = now
        // ease towards the reading pose (0.4 s); everything else is set directly
        readRot = if (readMode(ui)) slerp(readRot, target, 0.35f) else target
        c.setLocal(rotation = readRot)
    }

    private var lastSpinMs = 0L

    private fun slerp(a: Quat4, b: Quat4, t: Float): Quat4 {
        var d = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
        val bb = if (d < 0) Quat4(-b.x, -b.y, -b.z, -b.w) else b
        d = abs(d)
        if (d > 0.9995f) return bb
        return Quat4(a.x + (bb.x - a.x) * t, a.y + (bb.y - a.y) * t, a.z + (bb.z - a.z) * t, a.w + (bb.w - a.w) * t).normalized()
    }

    // ---- sticker colours

    private fun recolorAll(ui: CtUiState, now: Long, force: Boolean = false) {
        val k = kit ?: return
        val read = if (readMode(ui)) ui.read else null
        val readFace = read?.target
        val suspects = read?.failure?.suspects?.toSet() ?: emptySet()
        val pulseOn = (now / 500) % 2 == 0L
        val why = ui.why
        if (why != null && relevantFor != why.stage) {
            relevantFor = why.stage
            relevant = relevantFacelets(why.stage)
        }
        val celebrate = now - celebrateAt < CELEBRATE_MS
        for (i in 0 until 54) {
            val e = stickers[i] ?: continue
            val color = displayed[i]
            val dim =
                when {
                    read != null && !read.reconcile -> readFace != null && Facelets.faceOf(i) != readFace && read.failure == null
                    read != null && read.failure != null -> Facelets.faceOf(i) !in suspects.map { Facelets.faceOf(it) }
                    why != null -> relevant?.contains(i) == false
                    else -> false
                }
            val m: Material =
                when {
                    read?.fixing == i && pulseOn -> k.flash
                    i in suspects && pulseOn -> k.suspect
                    read != null && i in read.amber && pulseOn -> k.amber
                    // inferred, not scanned (user addendum): a slow blink to the dimmed shade
                    read != null && i in read.inferred && (now / 700) % 2 == 0L -> k.sticker(color, true)
                    celebrate && abs(((now - celebrateAt) / 28L) - i) < 2 -> k.flash
                    else -> k.sticker(color, dim)
                }
            if (force || stickerMat[i] !== m) {
                stickerMat[i] = m
                e.setMaterial(m)
            }
        }
    }

    /** Facelets the stage's demo moves touch: the rest is dimmed while "看为什么" plays (design S13). */
    private fun relevantFacelets(stage: Int): Set<Int> {
        val turns = Lessons.info(stage).demo.flatMap { Moves.token(it.first) }
        return (0 until 54).filter { i -> turns.any { t -> Facelets.pos[i][t.axis.v] in t.layers } }.toSet()
    }

    // ---- handles, bar, tray (design input.handles / dpad, S16–S27)

    private fun labelZh(ui: CtUiState): Boolean =
        when (ui.prefs.lang) {
            Lang.ZH -> true
            Lang.EN -> false
            Lang.SYSTEM -> appContext?.resources?.configuration?.locales?.get(0)?.language == "zh"
        }

    private fun stepHandles(ui: CtUiState, now: Long) {
        val k = kit ?: return
        val zh = labelZh(ui)
        val reading = readMode(ui)
        val inLesson = ui.tab == Tab.LEARN && ui.lesson != null && !ui.lesson.solved
        val cue = if (inLesson) ui.lessonCue else null
        val pulse = (now / 450) % 2 == 0L
        val sel = ui.selection
        for (h in Handle.every) {
            val holder = handlePlates[h] ?: continue
            val visible =
                !reading && ui.why == null &&
                    if (inLesson) {
                        // lesson: outer layers only; "whole" only on the row, and only for a y step (design input.lesson)
                        when {
                            h.slot == 0 -> false
                            h.slot == Handle.ALL -> h.row == Row.ROW && cue?.first == h
                            else -> true
                        }
                    } else true
            holder.on(visible)
            if (!visible) continue
            val selected = sel != null && sel.row == h.row && (if (h.slot == Handle.ALL) sel.whole else h.slot in sel.layers)
            val look =
                when {
                    cue?.first == h && pulse && !selected -> Labels.Look.CUE
                    selected -> Labels.Look.SELECTED
                    else -> Labels.Look.NORMAL
                }
            val m = k.handleLabel(handleText(h, zh), rowColor(h.row), look)
            if (handleMat[h] !== m) {
                handleMat[h] = m
                handleLabels[h]?.setMaterial(m)
            }
        }
        // the bar: the picked row's two arrows (or the lesson cue's), its name in the middle, or the idle line
        val barRow = sel?.row ?: cue?.first?.row
        val showBar = !reading && ui.why == null
        dpadBar?.on(showBar)
        val text =
            when {
                ui.hint?.kind == HintKind.NO_PICK -> if (zh) "先点一个把手" else "Tap a handle first"
                ui.hint?.kind == HintKind.ROW_LR -> if (zh) "横行往左或往右推" else "Rows go left or right"
                ui.hint?.kind == HintKind.COL_UD -> if (zh) "竖列往上或往下推" else "Columns go up or down"
                sel != null -> selectionLabel(sel, zh)
                cue != null -> handleText(cue.first, zh) + if (zh) " · 跟着亮的箭头" else " · follow the arrow"
                else -> localized(R.string.bar_idle, zh)
            }
        if (text != barText) {
            barText = text
            barLabel?.setMaterial(k.strip(text))
        }
        for (i in 0..1) {
            val a = arrowEnt[i] ?: continue
            a.on(showBar && barRow != null)
            if (barRow == null) continue
            val (pa, pb) = Handles.arrows(barRow)
            val push = if (i == 0) pa else pb
            val cueThis = cue != null && cue.first.row == barRow && cue.second == push
            val look = if (cueThis && pulse) Labels.Look.CUE else if (sel != null) Labels.Look.SELECTED else Labels.Look.NORMAL
            val m = k.arrow(push, barRow == Row.DEPTH, look)
            if (arrowMat[i] !== m) {
                arrowMat[i] = m
                arrowLabel[i]?.setMaterial(m)
            }
        }
        val trayText = localized(R.string.tray, zh)
        trayLabel?.let { tl -> k.strip(trayText, wide = true).let { m -> if (trayMat !== m) { trayMat = m; tl.setMaterial(m) } } }
    }

    private var trayMat: Material? = null

    private fun localized(id: Int, zh: Boolean): String {
        val ctx = appContext ?: return ""
        return localizedResources(ctx, if (zh) Lang.ZH else Lang.EN).getString(id)
    }

    private fun rowColor(r: Row): Int = when (r) { Row.ROW -> Labels.ROW; Row.COL -> Labels.COL; Row.DEPTH -> Labels.DEPTH }

    private fun handleText(h: Handle, zh: Boolean): String {
        if (h.slot == Handle.ALL) return if (zh) "整只" else "All"
        return when (h.row) {
            Row.ROW -> when (h.slot) { 1 -> if (zh) "上" else "Top"; 0 -> if (zh) "中" else "Mid"; else -> if (zh) "下" else "Bot" }
            Row.COL -> when (h.slot) { -1 -> if (zh) "左" else "L"; 0 -> if (zh) "中" else "Mid"; else -> if (zh) "右" else "R" }
            Row.DEPTH -> when (h.slot) { 1 -> if (zh) "前" else "Front"; 0 -> if (zh) "中" else "Mid"; else -> if (zh) "后" else "Back" }
        }
    }

    private fun selectionLabel(sel: com.kejing.cubetutor.domain.Selection, zh: Boolean): String {
        if (sel.whole) return if (zh) "整只" else "Whole cube"
        val names = sel.layers.sortedDescending().joinToString(if (zh) "+" else " + ") { handleText(Handle(sel.row, it), zh) }
        val kind = when (sel.row) { Row.ROW -> if (zh) "行" else "row"; Row.COL -> if (zh) "列" else "column"; Row.DEPTH -> if (zh) "片" else "slice" }
        return if (zh) "「$names」$kind" else "$names $kind"
    }

    // ---- ghost layer: the layer to turn, copied in 12 % white (user 2026-11-18: 35 % hid the colours), pulled out 4 cm along its axis, turning in a loop

    private fun stepGhost(ui: CtUiState, now: Long) {
        val inLesson = ui.tab == Tab.LEARN && ui.lesson != null
        val step = if (inLesson) ui.lesson?.step else null
        val cueOk = inLesson && ui.lessonCue != null && spin == null && CtController.anims.isEmpty()
        val tokens = step?.let { Moves.token(it.move) }
        val turn = ui.lesson?.halfTurn ?: tokens?.first()
        if (!cueOk || turn == null || turn.whole || turn.layers.size != 1) {
            ghosts.forEach { it.on(false) }
            return
        }
        // a half turn not started yet turns the full 180° (slower), then holds: it must not look like a quarter turn
        val quarters = if (ui.lesson?.halfTurn == null && tokens != null && tokens.size == 2) 2 else 1
        val turnMs = if (quarters == 2) GHOST_HALF_MS else GHOST_TURN_MS
        val loopMs = if (quarters == 2) GHOST_HALF_LOOP_MS else GHOST_LOOP_MS
        val layer = turn.layers.first()
        val axis = when (turn.axis) { Axis.X -> Vec3(1f, 0f, 0f); Axis.Y -> Vec3(0f, 1f, 0f); Axis.Z -> Vec3(0f, 0f, 1f) }
        val out = axis * (0.04f * layer)
        val phase = ((now % loopMs).toFloat() / turnMs).coerceIn(0f, 1f)
        val q = turnRot(turn, quarters * (0.5f - 0.5f * cos(PI.toFloat() * phase)))
        var n = 0
        for ((key, home) in cubieHome) {
            if (!inLayer(key, turn)) continue
            val g = ghosts.getOrNull(n++) ?: break
            g.on(true)
            g.setLocal(position = q.rotate(home) + out, rotation = q)
        }
        while (n < ghosts.size) ghosts[n++].on(false)
    }

    // ---- outline: the picked layers in white; the turn just made in green (right) / amber (off-script)

    private fun stepOutline(ui: CtUiState, now: Long) {
        val k = kit ?: return
        val o = outline ?: return
        val l = ui.lesson
        val lt = lastTurn
        val (axis, layers, mat, tag) =
            when {
                l?.offScript != null && lt != null && now - lastTurnAt < 1500 -> Quad(lt.axis, lt.layers, k.outlineAmber, "amber")
                l != null && l.correctAt > 0 && java.lang.System.currentTimeMillis() - l.correctAt < 450 && lt != null -> Quad(lt.axis, lt.layers, k.outlineGreen, "green")
                ui.selection != null && !readMode(ui) -> Quad(ui.selection.row.axis, ui.selection.layers, k.outlineWhite, "white")
                else -> Quad(null, emptySet(), k.outlineWhite, "none")
            }
        if (axis == null) {
            o.on(false)
            outlineKey = "none"
            return
        }
        val key = "$axis$layers$tag"
        if (key != outlineKey) {
            outlineKey = key
            val b = MeshBuilder(64)
            val ext = 1.5f * SP + 0.004f
            for (layer in layers) {
                val lo = FloatArray(3) { -ext }
                val hi = FloatArray(3) { ext }
                lo[axis.v] = layer * SP - SP / 2 - 0.002f
                hi[axis.v] = layer * SP + SP / 2 + 0.002f
                b.addBoxEdges(Vec3(lo[0], lo[1], lo[2]), Vec3(hi[0], hi[1], hi[2]), 0.0035f)
            }
            val mesh = outlineMesh
            if (mesh == null || !b.replace(mesh)) {
                // first outline, or the engine refused the refill: a new entity with its own mesh
                val m = b.build("outline")
                shared?.keep(m)
                outlineMesh = m
                runCatching { o.destroy(true) }
                val parent = cubeRoot ?: return
                outline = model(m, mat, "outline", parent)
                return
            }
            o.setMaterial(mat)
        }
        o.on(true)
    }

    private data class Quad(val axis: Axis?, val layers: Set<Int>, val mat: Material, val tag: String)

    // ---- the app-drawn controller ray (D41): pose -> raycast against the app's own colliders; trigger = tap / hold tray

    private fun stepRay(ui: CtUiState, now: Long) {
        // B clears the pick (design input.joystick); the stick turns the picked layers (one push = 90°)
        repeat(controllers.takeBPresses()) { CtController.onEvent(Ui.ClearPick("B")) }
        val (sx, sy) = controllers.stick()
        pushes.step(sx, sy)?.let { p -> CtController.onEvent(Ui.PushStick(p, "stick")) }
        // v0.1.2 (user on the headset: 「一个手柄两个射线」): in Full Space the system draws the controller ray and its
        // trigger already reaches us as a tap through board-input. The app no longer draws a second ray or turns its
        // own trigger into a tap (that made one press toggle a handle twice). Tray drag goes through board-input too.
        beam?.on(false)
        dot?.on(false)
    }

    /** One path for a 3D hit, whatever the input (board-input listener, the controller ray, debug `tap=`). */
    private val dedupe = TapDeduper()

    fun onTarget(t: CubeTarget, src: String) {
        if (!dedupe.accept(t.toString(), SystemClock.elapsedRealtime())) {
            CtLog.i("INPUT tap src=$src target=$t dropped (same press seen again)")
            return
        }
        CtLog.i("INPUT tap src=$src target=$t")
        when (t) {
            is CubeTarget.Sticker -> CtController.onEvent(Ui.TapSticker(t.index, src))
            is CubeTarget.HandleT -> CtController.onEvent(Ui.TapHandle(t.handle, src))
            is CubeTarget.Arrow -> CtController.onEvent(Ui.Arrow(t.first, src))
            CubeTarget.Tray, CubeTarget.Chip -> Unit
        }
    }

    // ---- tray drag (design I27 / S29, D1: follow the hand, stop where released, then turn to you about the vertical)

    fun startDrag(src: String) {
        val rp = rigPose ?: return
        dragStart = rp
        CtController.onDragging(true)
        CtLog.i("DRAG start src=$src rig=${CtLog.pos(rp.position)}")
    }

    fun moveDrag(delta: Vec3) {
        val s = dragStart ?: return
        setRig(Upright(s.position + delta, s.yaw))
    }

    fun endDrag(src: String) {
        val rp = rigPose ?: return
        if (dragStart == null) return
        dragStart = null
        // D14: not closer than 0.6 m (horizontal) to your head
        val flat = Vec3(rp.position.x - pose.position.x, 0f, rp.position.z - pose.position.z)
        val dist = flat.length
        val p = if (dist < RigLayout.DIST_M && dist > 1e-3f) Vec3(pose.position.x, rp.position.y, pose.position.z) + flat.normalized() * RigLayout.DIST_M else rp.position
        setRig(RigLayout.facing(p, pose.position))
        CtController.onDragging(false)
        CtLog.i(String.format(Locale.US, "DRAG end src=%s rig=%s dist=%.2f yaw=%.1f", src, CtLog.pos(p), hypot(p.x - pose.position.x, p.z - pose.position.z), Math.toDegrees(rigPose!!.yaw.toDouble())))
    }

    // ---- Stage panels: "打开面板" over the rig, the one-line hint on the line of sight

    private fun placePanels(ui: CtUiState, now: Long) {
        val pr = panels ?: return
        val hintOn = ui.loading || WindowCtl.levelHint || WindowCtl.recentering
        if (hintOn) {
            val pitchDeg = Math.toDegrees(pose.pitch.toDouble()).toFloat().coerceIn(-40f, 40f)
            val h = placeUpright(pose, 0f, 1.2f, -pitchDeg + 4f)
            pr[PanelIds.HINT]?.let { s -> if (s.shown) s.moveTo(h.position) else s.show(h.position) }
        } else pr[PanelIds.HINT]?.hide()
        val rp = rigPose
        if (rp != null && WindowCtl.chipWanted(now) && !ui.loading) {
            val at = rp.position + rp.rotation.rotate(Vec3(0f, 0.22f, 0.05f))
            pr[PanelIds.CHIP]?.let { s -> if (s.shown) s.moveTo(at) else s.show(at); s.facePoint = pose.position }
        } else pr[PanelIds.CHIP]?.hide()
        placeScanCard(ui, now)
    }

    /**
     * The alignment frame (user addendum 2026-11-11 「可以显示一个框，引导用户把一面对在里面」): locked to the head at
     * [FRAME_OFFSET], scaled so the 160 dp box is 5.7 cm = a cube held at that distance. Shown while the camera reads
     * (S02 / S03) only. Head pose is used for this placement only (D1), never to aim or pick.
     */
    private fun placeScanCard(ui: CtUiState, now: Long) {
        val slot = panels?.get(PanelIds.SCAN) ?: return
        val r = ui.read
        val on = readMode(ui) && r != null && r.mode == ReadMode.SCAN && r.failure == null
        if (!on) {
            slot.hide()
            return
        }
        val at = pose.position + pose.rotation.rotate(FRAME_OFFSET)
        if (slot.shown) slot.moveTo(at) else slot.show(at)
        slot.facePoint = pose.position
    }

    // =========================================================================================== logs / debug

    private fun stepLogs(ui: CtUiState, now: Long) {
        val since = now - startMs
        if (startupLogs < 3 && since > STARTUP_LOG_MS[startupLogs]) {
            startupLogs++
            CtLog.i("STARTUP t=${since / 1000}s ${where()} window=${WindowCtl.panelState} state=${ui.designState}")
        }
    }

    /** Head-relative placement of the rig (D1 1/3/5 s startup log, debug `where`). */
    fun where(): String {
        val rp = rigPose ?: return "rig=none head=${CtLog.pos(pose.position)} hasHead=$hasHead"
        val d = hypot(rp.position.x - pose.position.x, rp.position.z - pose.position.z)
        val bearing = RigLayout.bearingDeg(pose.position, pose.yaw(0f), rp.position)
        return String.format(Locale.US, "rig=%s dist=%.2f bearing=%.1f° below=%.2f visible=%s head=%s", CtLog.pos(rp.position), d, bearing, pose.position.y - rp.position.y, rig?.enabled, CtLog.pos(pose.position))
    }

    fun logInstances(): List<String> {
        val lines = ArrayList<String>()
        lines += "INSTANCE cubies=${cubies.size} mesh=roundedBox(0.0653, r 6 mm, ${kit?.cubieTris} tris) material=PBR opaque"
        lines += "INSTANCE stickers=${stickers.count { it != null }} tile=$TILE m opaque PBR, collider ${TILE}x${TILE}x0.006 + Interactable + HoverEffect (reading: only the active face)"
        lines += "INSTANCE handles=${handlePlates.size} slab 0.05x0.05x0.006 + label, collider ${HANDLE_HIT}x${HANDLE_HIT}x0.02"
        lines += "INSTANCE bar=1 arrows=2 (collider 0.08) tray=1 (collider 0.32x0.07) ghosts=${ghosts.size} (12% white, no collider) ray=system (app ray off since v0.1.2)"
        lines.forEach { CtLog.i(it) }
        return lines
    }

    fun debugTarget(t: CubeTarget) = onTarget(t, "debug")

    /** Debug `drag=start|move dx dz|end`: feeds the same drag path as the tray. */
    fun debugDrag(phase: String, dx: Float, dy: Float, dz: Float) {
        when (phase) {
            "start" -> startDrag("debug")
            "move" -> moveDrag(Vec3(dx, dy, dz))
            "end" -> endDrag("debug")
        }
    }

    fun rigPoseOrNull(): Upright? = rigPose

    fun headPose(): HeadPose = pose

    companion object {
        const val CUBIE = 0.0653f
        const val GAP = 0.002f
        const val SP = CUBIE + GAP

        /** Visible sticker = its collider (design readPose.sticker). */
        const val TILE = ReadPose.TILE

        /** Handle collider = the visible plate (design q1-3 rule). */
        const val HANDLE_HIT = ReadPose.HANDLE_HIT

        /** Reading pose (design spatialLayout.cube.readPose): cubie pitch 7.5 cm, 0.35 s, face-on tilted 14°. */
        const val SP_READ = ReadPose.SP_READ
        const val EXPLODE_MS = 350f
        val FACE_ON: Quat4 = Quat4.axisAngle(Vec3(1f, 0f, 0f), Math.toRadians(14.0).toFloat())
        const val HALF = CUBIE / 2f
        const val SUN_LUX = 1800f
        const val NO_HEAD_MS = 5000L
        const val CELEBRATE_MS = 2000L
        const val GHOST_LOOP_MS = 1800L
        const val GHOST_TURN_MS = 1200f

        /** Half turn: 180° in 1.8 s, then 1.2 s still on the turned layer. */
        const val GHOST_HALF_MS = 1800f
        const val GHOST_HALF_LOOP_MS = 3000L
        const val RAY_MAX = 3f
        const val RAY_IDLE = 1.5f
        const val RAY_W = 0.003f
        const val RAY_DOT = 0.014f
        const val PANEL_SCALE = 0.75f
        const val CHIP_W = 200f
        const val CHIP_H = 76f
        const val HINT_W = 560f
        const val HINT_H = 80f
        /** Alignment-frame panel: text on top, the 160 dp box below (dp; [FRAME_K] makes the box 5.7 cm). */
        const val SCAN_W = 320f
        const val SCAN_H = 276f
        const val FRAME_BOX_DP = 160f

        /** Attachment panels are 0.8 mm/dp at scale 1 (window measurement); box 160 dp → 0.057 m. */
        const val FRAME_K = 0.057f / (FRAME_BOX_DP * 0.0008f)

        /** Box centre 0.33 m ahead, 8° below the line of sight; the panel centre sits 55 dp (scaled) above it. */
        val FRAME_OFFSET = Vec3(0f, -0.33f * 0.1405f + 55f * 0.0008f * FRAME_K, -0.33f)
        private val STARTUP_LOG_MS = longArrayOf(1000L, 3000L, 5000L)

        /** The fixed 3/4 view (design spatialLayout.cube.defaultView): yaw −30°, then pitch the top 25° towards you. */
        val PRESENT: Quat4 = Quat4.axisAngle(Vec3(1f, 0f, 0f), Math.toRadians(25.0).toFloat()) * Quat4.axisAngle(Vec3(0f, 1f, 0f), Math.toRadians(-30.0).toFloat())

        /** cubeRoot turn while reading face n (design S02 / cubeSpec.readOrder): that face comes to the front. */
        val READ_ROT: Array<Quat4> =
            arrayOf(
                Quat4.IDENTITY,
                Quat4.axisAngle(Vec3(0f, 1f, 0f), (-PI / 2).toFloat()),
                Quat4.axisAngle(Vec3(0f, 1f, 0f), PI.toFloat()),
                Quat4.axisAngle(Vec3(0f, 1f, 0f), (PI / 2).toFloat()),
                Quat4.axisAngle(Vec3(1f, 0f, 0f), (PI / 2).toFloat()),
                Quat4.axisAngle(Vec3(1f, 0f, 0f), (-PI / 2).toFloat()),
            )

        val RESOLVER: TargetResolver<CubeTarget> = TargetResolver.byName { CubeNames.resolveName(it) }

        fun code(x: Int, y: Int, z: Int) = (x + 1) * 9 + (y + 1) * 3 + (z + 1)

        fun decode(k: Int) = intArrayOf(k / 9 - 1, (k / 3) % 3 - 1, k % 3 - 1)
    }
}

/** Meshes, materials and label textures (built off the main thread; labels baked lazily and cached). */
internal class Kit(private val sh: SharedResources) {
    private val m = SceneMaterials(sh)
    val cubieMesh: MeshResource
    val cubieTris: Int
    val tile = sh.keep(MeshBuilder(32).apply { addRoundedRect(CubeScene.TILE, CubeScene.TILE, 0.007f) }.build("tile"))
    val handleSlab = sh.keep(MeshBuilder(200).apply { addRoundedBox(0.025f, 0.025f, 0.003f, 0.0025f, 3) }.build("handleSlab"))
    val handleFace = sh.keep(MeshBuilder(32).apply { addRoundedRect(0.047f, 0.047f, 0.0075f) }.build("handleFace"))
    val barSlab = sh.keep(MeshBuilder(200).apply { addRoundedBox(0.14f, 0.04f, 0.003f, 0.0025f, 3) }.build("barSlab"))
    val barFace = sh.keep(MeshBuilder(32).apply { addRoundedRect(0.135f, 0.05f, 0.01f) }.build("barFace"))
    val arrowSlab = sh.keep(MeshBuilder(200).apply { addRoundedBox(0.0325f, 0.0325f, 0.003f, 0.0025f, 3) }.build("arrowSlab"))
    val arrowFace = sh.keep(MeshBuilder(32).apply { addRoundedRect(0.062f, 0.062f, 0.012f) }.build("arrowFace"))
    val traySlab = sh.keep(MeshBuilder(200).apply { addRoundedBox(0.15f, 0.025f, 0.003f, 0.0025f, 3) }.build("traySlab"))
    val trayFace = sh.keep(MeshBuilder(32).apply { addRoundedRect(0.29f, 0.042f, 0.01f) }.build("trayFace"))
    val block = sh.keep(Shapes.unitBlock("rayBeam"))
    val disc = sh.keep(Shapes.disc("disc", 24))

    init {
        val b = MeshBuilder(256)
        b.addRoundedBox(CubeScene.HALF, CubeScene.HALF, CubeScene.HALF, 0.006f, 5)
        cubieTris = b.indices.size / 3
        cubieMesh = sh.keep(b.build("cubie"))
    }

    fun emptyOutline(): MeshResource = sh.keep(MeshBuilder(8).build("outline0"))

    val body = m.pbr(CtColors.Body, 0.45f)
    private val stickerPbr = HashMap<CubeColor, Material>()
    private val stickerDim = HashMap<CubeColor, Material>()

    fun sticker(c: CubeColor, dim: Boolean): Material {
        val map = if (dim) stickerDim else stickerPbr
        return map.getOrPut(c) {
            val col = when (c) {
                CubeColor.WHITE -> CtColors.StickerWhite
                CubeColor.YELLOW -> CtColors.StickerYellow
                CubeColor.RED -> CtColors.StickerRed
                CubeColor.ORANGE -> CtColors.StickerOrange
                CubeColor.BLUE -> CtColors.StickerBlue
                CubeColor.GREEN -> CtColors.StickerGreen
                CubeColor.NONE -> CtColors.Unfilled
            }
            // reading: other faces darker but opaque (design S02: 调暗用颜色，不用透明)
            val use = if (dim) Color(col.red * 0.32f, col.green * 0.32f, col.blue * 0.32f, 1f) else col
            m.pbr(use, 0.30f)
        }
    }

    val suspect = m.unlit(CtColors.Suspect)
    val amber = m.unlit(CtColors.Interaction)
    val flash = m.unlit(Color.White)
    val ghost = m.sheer(Color.White, 0.12f) // user 2026-11-18: 0.35 hid the colours of the layer behind
    val outlineWhite = m.unlit(CtColors.HighlightRim)
    val outlineGreen = m.unlit(CtColors.Passable)
    val outlineAmber = m.unlit(CtColors.Interaction)
    val rayWhite = m.unlit(Color.White)
    val rayGold = m.unlit(CtColors.Interaction)
    val handleBack = m.pbr(Color(0xFF242A31), 0.6f) // design-style: fixed-figma-color handle plate
    val trayBack = m.pbr(CtColors.Tray, 0.6f)
    val blank = m.unlit(Color(0xFF242A31)) // design-style: fixed-figma-color label before its texture

    private val labels = HashMap<String, Material>()

    fun handleLabel(text: String, rowColor: Int, look: Labels.Look): Material =
        labels.getOrPut("h|$text|$rowColor|$look") { m.unlit(Color.White, m.texture(Labels.handle(text, rowColor, look))) }

    fun arrow(p: Push, curved: Boolean, look: Labels.Look): Material =
        labels.getOrPut("a|$p|$curved|$look") { m.unlit(Color.White, m.texture(Labels.arrow(p, curved, look))) }

    fun strip(text: String, wide: Boolean = false): Material =
        labels.getOrPut("s|$text|$wide") { m.unlit(Color.White, m.texture(if (wide) Labels.strip(text, 1024, 148) else Labels.strip(text, 540, 200))) }
}
