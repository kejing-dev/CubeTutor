package com.kejing.cubetutor.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.kejing.cubetutor.domain.scan.GridFinder
import com.kejing.cubetutor.domain.scan.ScanFrame
import com.kejing.cubetutor.domain.scan.ScanSession
import com.kejing.cubetutor.platform.CtLog
import com.kejing.cubetutor.platform.camera.CameraFrameSource
import com.kejing.cubetutor.platform.camera.FrameSource
import com.kejing.cubetutor.platform.camera.InjectedFrameSource
import java.io.File
import java.util.concurrent.Executors

/**
 * Frames → grid finding on one worker thread (never the main thread, never more than one frame in flight, so a slow
 * frame drops the next instead of queueing) → [ScanSession] on the main thread → [CtController]. One scan at a time;
 * [stop] releases the camera (D41: open → close → open works).
 */
object ScanBridge {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "scan-grid").apply { priority = Thread.NORM_PRIORITY - 1 } }
    @Volatile private var busy = false
    @Volatile private var epoch = 0
    @Volatile private var pauseUntil = 0L
    private var source: FrameSource? = null
    var session: ScanSession? = null
        private set

    /** Debug builds: a factory for injected pictures; when set it replaces the camera (design emulatorVerification). */
    var injector: ((Context) -> FrameSource)? = null

    /** Debug: pretend the camera failed / permission was denied (S05). */
    var forceFail = false
    var forceDeny = false

    /** Where the alignment frame is in the camera frame (debug `roi=x,y,w,h`); spike calibrates it. */
    @Volatile var roi: GridFinder.Roi = GridFinder.Roi.DEFAULT

    /** Last frame width (the frame UI judges "too far" against the ROI). */
    @Volatile var frameW = 640
        private set

    /** Debug `scanpause=1`: sessions stay at "stable" (gold frame) without taking the face. */
    var holdStable = false

    /** Debug `scanfake=<9 letters>`: skip the grid finder, these colours are the frame's result. */
    var fake: String? = null

    /** The last frame seen (debug `dumpframe=1`, spike S3). */
    @Volatile var lastFrame: ScanFrame? = null
        private set

    @Volatile var frames = 0
        private set

    val running: Boolean
        get() = source != null

    /** The PICO emulator has no passthrough camera, and a camera session there hangs on destroy (feedback). */
    val isEmulator: Boolean
        get() = Build.DEVICE.contains("emulator", true) || Build.PRODUCT.contains("emulator", true) || Build.HARDWARE.contains("ranchu", true)

    fun start(context: Context, s: ScanSession, onEvent: (ScanSession.Event) -> Unit, onFail: (FrameSource.Failure, String) -> Unit): Boolean {
        stop("restart")
        val src =
            injector?.invoke(context)
                ?: if (isEmulator) {
                    CtLog.i("SCAN emulator without injected pictures: no camera")
                    return false
                } else CameraFrameSource(context.applicationContext)
        val my = ++epoch
        session = s
        source = src
        frames = 0
        busy = false
        pauseUntil = 0L
        CtLog.i("SCAN start source=${src.name} only=${s.reads.size}")
        src.start(
            sink = { f -> onFrame(my, f, onEvent) },
            fail = { why, msg -> main.post { if (epoch == my) { CtLog.w("SCAN source failed $why $msg"); stop("fail"); onFail(why, msg) } } },
        )
        return true
    }

    private fun onFrame(my: Int, f: ScanFrame, onEvent: (ScanSession.Event) -> Unit) {
        lastFrame = f
        frameW = f.w
        frames++
        if (busy || epoch != my) return
        busy = true
        worker.execute {
            val t0 = SystemClock.elapsedRealtime()
            val fk = fake
            val grid =
                if (fk != null) FakeGrid.of(fk)
                else runCatching { GridFinder.find(f, roi) }.onFailure { CtLog.w("SCAN grid failed: $it") }.getOrNull()
            val ms = SystemClock.elapsedRealtime() - t0
            if (frames <= 3 || frames % 60 == 0) CtLog.i("SCAN frame ${frames} grid=${grid != null} ${ms} ms")
            main.post {
                busy = false
                if (epoch != my) return@post
                val now = SystemClock.elapsedRealtime()
                if (now < pauseUntil) return@post
                val s = session ?: return@post
                s.holdAtStable = holdStable
                val e = s.onGrid(grid, now)
                if (e is ScanSession.Event.Confirmed) (source as? InjectedFrameSource)?.advance()
                onEvent(e)
            }
        }
    }

    /** Hold the reader for [ms] (after a face is taken: the reference sample pauses so the next face is not cross-read). */
    fun pause(ms: Long) {
        pauseUntil = SystemClock.elapsedRealtime() + ms
    }

    fun resume() {
        pauseUntil = 0L
    }

    fun stop(why: String) {
        val s = source ?: return
        epoch++
        source = null
        session = null
        runCatching { s.stop() }
        CtLog.i("SCAN stop why=$why frames=$frames")
    }

    /** Saves the last frame as a PNG under files/scan-dump/ (debug `dumpframe=1`, spike S3). */
    fun dump(context: Context, tag: String): String? {
        val f = lastFrame ?: return null
        val dir = File(context.getExternalFilesDir(null), "scan-dump").apply { mkdirs() }
        val out = File(dir, "frame-$tag-${System.currentTimeMillis()}.png")
        out.outputStream().use { InjectedFrameSource.toBitmap(f).compress(Bitmap.CompressFormat.PNG, 100, it) }
        CtLog.i("SCAN dumped ${out.path}")
        return out.path
    }
}

/** `scanfake=WWRGBOYYW`: a grid with exactly these colours (only tests the flow, design inject). */
internal object FakeGrid {
    fun of(code: String): com.kejing.cubetutor.domain.scan.Grid? {
        if (code.length != 9) return null
        val cells =
            code.mapIndexed { i, ch ->
                val lab = com.kejing.cubetutor.domain.scan.ColorModel.DEFAULTS[com.kejing.cubetutor.domain.CubeColor.ofCode(ch)] ?: return null
                val rgb = labToRgb(lab)
                com.kejing.cubetutor.domain.scan.Cell((i % 3) * 20f, (i / 3) * 20f, rgb[0], rgb[1], rgb[2])
            }
        return com.kejing.cubetutor.domain.scan.Grid(cells, 20f)
    }

    // the defaults were made from these sRGB values (ColorModel.DEFAULTS); look them up instead of inverting Lab
    private fun labToRgb(lab: com.kejing.cubetutor.domain.scan.Lab): IntArray {
        val table = listOf(intArrayOf(225, 228, 225), intArrayOf(225, 205, 35), intArrayOf(185, 25, 40), intArrayOf(245, 115, 30), intArrayOf(20, 75, 185), intArrayOf(25, 160, 75))
        return table.minByOrNull { com.kejing.cubetutor.domain.scan.Lab.ofRgb(it[0], it[1], it[2]).dist(lab) }!!
    }
}
