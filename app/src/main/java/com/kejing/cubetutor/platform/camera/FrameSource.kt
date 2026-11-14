package com.kejing.cubetutor.platform.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.kejing.cubetutor.domain.scan.ScanFrame
import com.kejing.cubetutor.platform.CtLog
import com.pico.spatial.ml.readback.readbackContentSuspend
import com.pico.spatial.ml.securemr.SpatialMLInstance
import com.pico.spatial.ml.securemr.SpatialMLSession
import com.pico.spatial.ml.securemr.Tensor
import java.io.File
import java.util.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where scan frames come from (design emulatorVerification.rule): the headset camera readback, or injected pictures
 * (debug builds). Everything after the frame (grid, colours, stability, the six-face check) is the same code.
 */
interface FrameSource {
    /** Why a source gave up (design S05). */
    enum class Failure { NO_FRAMES, ERRORS, SESSION }

    val name: String

    fun start(sink: (ScanFrame) -> Unit, fail: (Failure, String) -> Unit)

    fun stop()
}

/**
 * SpatialML readback mode (design cameraCapability.route): a session with no container (0 x 0 x 0, DISABLED), one
 * pipeline `rectifiedVSTAccess(right eye 640 x 480 RGB UINT8)` straight into a global tensor, submitted every
 * [PERIOD_MS] and read back as bytes (not a texture: row padding, feedback ⑤). Known issues handled (feedback ①–④):
 * create / destroy on IO with 3 s + 8 s timeouts, every call in try, 3 failures in a row or 8 s without a non-zero
 * frame = camera unavailable. Never started on the emulator (session destroy hangs after a camera submit).
 * Logs use the fixed tag via [CtLog] with the prefix CAM (RmClient floods logcat on the headset, feedback ⑦).
 */
class CameraFrameSource(private val app: Context) : FrameSource {
    override val name = "camera"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var session: SpatialMLSession? = null

    override fun start(sink: (ScanFrame) -> Unit, fail: (FrameSource.Failure, String) -> Unit) {
        if (job != null) return
        job =
            scope.launch {
                val t0 = SystemClock.elapsedRealtime()
                try {
                    val inst = SpatialMLInstance.create(app)
                    withTimeoutOrNull(3000) { while (!inst.ready) delay(50) }
                    CtLog.i("CAM instance ready=${inst.ready} after ${SystemClock.elapsedRealtime() - t0} ms")
                    // createSession is a synchronous binder call that can hang behind an old destroy (feedback ②)
                    val pending = scope.async { inst.createSession(SpatialMLSession.InitInfo(W, H, 0, 0, 0, SpatialMLSession.ContainerType.DISABLED)) }
                    val s = withTimeoutOrNull(8000) { pending.await() }
                    if (s == null) {
                        CtLog.w("CAM session timeout / null")
                        // a session that arrives late is destroyed at once
                        pending.invokeOnCompletion { e -> if (e == null) scope.launch { runCatching { pending.await()?.let { destroy(it) } } } }
                        fail(FrameSource.Failure.SESSION, "session")
                        return@launch
                    }
                    if (!isActive) {
                        destroy(s)
                        return@launch
                    }
                    session = s
                    val global = s.newGlobalTensor(Tensor.MultiDimensionalInitInfo(Tensor.DataType.UINT8, intArrayOf(H, W), 3))
                    val pipe = s.newPipeline()
                    val ph = pipe.newPlaceholderLike(global)
                    pipe.rectifiedVSTAccess(ph, null, null, null)
                    CtLog.i("CAM session ok ${W}x$H in ${SystemClock.elapsedRealtime() - t0} ms")
                    var errors = 0
                    var n = 0
                    var lastGood = SystemClock.elapsedRealtime()
                    while (isActive) {
                        val tick = SystemClock.elapsedRealtime()
                        try {
                            pipe.submit(mapOf(ph to global), null, null)
                            delay(PERIOD_MS)
                            val bytes =
                                global.readbackContentSuspend().use { c ->
                                    val b = c.buffer.duplicate()
                                    b.rewind()
                                    if (b.remaining() < W * H * 3) {
                                        CtLog.w("CAM short buffer ${b.remaining()}")
                                        null
                                    } else ByteArray(W * H * 3).also { b.get(it) }
                                }
                            errors = 0
                            if (bytes != null) {
                                val f = ScanFrame(W, H, bytes, SystemClock.elapsedRealtime())
                                val level = f.meanLevel()
                                if (level > 2.0) {
                                    n++
                                    lastGood = f.tMs
                                    if (n <= 3 || n % 30 == 0) CtLog.i(String.format(java.util.Locale.US, "CAM frame %d bytes=%d mean=%.1f dt=%d", n, bytes.size, level, f.tMs - tick))
                                    sink(f)
                                } else if (n == 0) CtLog.w("CAM zero frame mean=$level")
                            }
                        } catch (t: Throwable) {
                            if (t is kotlinx.coroutines.CancellationException) throw t
                            errors++
                            CtLog.w("CAM error $errors: $t")
                            if (errors >= 3) {
                                fail(FrameSource.Failure.ERRORS, t.toString())
                                break
                            }
                        }
                        if (SystemClock.elapsedRealtime() - lastGood > NO_FRAME_MS) {
                            CtLog.w("CAM no frames for ${NO_FRAME_MS} ms (got $n)")
                            fail(FrameSource.Failure.NO_FRAMES, "no frames")
                            break
                        }
                    }
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) return@launch
                    CtLog.w("CAM start failed: $t")
                    fail(FrameSource.Failure.SESSION, t.toString())
                }
            }
    }

    override fun stop() {
        job?.cancel()
        job = null
        val s = session ?: return
        session = null
        destroy(s)
    }

    private fun destroy(s: SpatialMLSession) {
        // destroy can hang (feedback ①②): its own thread, never the main one
        Thread({
            val t0 = SystemClock.elapsedRealtime()
            runCatching { s.destructor.destroy() }.onFailure { CtLog.w("CAM destroy: $it") }
            CtLog.i("CAM session destroyed in ${SystemClock.elapsedRealtime() - t0} ms")
        }, "cam-destroy").start()
    }

    companion object {
        const val W = 640
        const val H = 480
        const val PERIOD_MS = 160L
        const val NO_FRAME_MS = 8000L
    }
}

/**
 * Debug pictures fed as camera frames at the camera's rate (design emulatorVerification.inject): one picture again and
 * again (`scanimg`), or a set face1…face6 that moves to the next picture when the reader takes the current one
 * (`scanset`, [advance]). [noise] adds per-frame brightness / colour-temperature jitter and pixel noise.
 */
class InjectedFrameSource(private val app: Context, private val pictures: List<String>, private val noise: Float = 0f, private val holdMs: Long = 0L) : FrameSource {
    override val name = "inject"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var index = 0
    @Volatile private var holdUntil = 0L
    private val rnd = Random(7)
    private val cache = HashMap<String, ScanFrame>()

    /** The reader took the current picture: next one (after the hold). */
    fun advance() {
        if (index < pictures.size - 1) {
            index++
            holdUntil = SystemClock.elapsedRealtime() + holdMs
        }
    }

    override fun start(sink: (ScanFrame) -> Unit, fail: (FrameSource.Failure, String) -> Unit) {
        if (job != null) return
        job =
            scope.launch {
                while (isActive) {
                    val p = pictures.getOrNull(index)
                    val f = p?.let { load(it) }
                    if (f == null) {
                        fail(FrameSource.Failure.NO_FRAMES, "cannot read $p")
                        break
                    }
                    if (SystemClock.elapsedRealtime() >= holdUntil) sink(jitter(f))
                    delay(CameraFrameSource.PERIOD_MS)
                }
            }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    private fun load(path: String): ScanFrame? =
        cache.getOrPut(path) {
            val bmp: Bitmap =
                runCatching {
                    val file = if (path.startsWith("/")) File(path) else File(app.getExternalFilesDir(null), path)
                    if (file.isFile) BitmapFactory.decodeFile(file.path) else app.assets.open(path).use { BitmapFactory.decodeStream(it) }
                }.getOrNull() ?: return null
            toFrame(bmp)
        }

    private fun jitter(f: ScanFrame): ScanFrame {
        if (noise <= 0f) return ScanFrame(f.w, f.h, f.rgb, SystemClock.elapsedRealtime())
        val gain = 1f + (rnd.nextFloat() - 0.5f) * 0.6f * noise
        val warm = (rnd.nextFloat() - 0.5f) * 0.3f * noise
        val out = ByteArray(f.rgb.size)
        val k = floatArrayOf(gain * (1f + warm), gain, gain * (1f - warm))
        for (i in out.indices) {
            val v = (f.rgb[i].toInt() and 0xFF) * k[i % 3] + rnd.nextGaussian().toFloat() * 12f * noise
            out[i] = v.toInt().coerceIn(0, 255).toByte()
        }
        return ScanFrame(f.w, f.h, out, SystemClock.elapsedRealtime())
    }

    companion object {
        fun toFrame(bmp: Bitmap): ScanFrame {
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val rgb = ByteArray(px.size * 3)
            for (i in px.indices) {
                rgb[i * 3] = (px[i] shr 16 and 0xFF).toByte()
                rgb[i * 3 + 1] = (px[i] shr 8 and 0xFF).toByte()
                rgb[i * 3 + 2] = (px[i] and 0xFF).toByte()
            }
            return ScanFrame(bmp.width, bmp.height, rgb, SystemClock.elapsedRealtime())
        }

        fun toBitmap(f: ScanFrame): Bitmap {
            val px = IntArray(f.w * f.h) { i -> (0xFF shl 24) or ((f.rgb[i * 3].toInt() and 0xFF) shl 16) or ((f.rgb[i * 3 + 1].toInt() and 0xFF) shl 8) or (f.rgb[i * 3 + 2].toInt() and 0xFF) }
            return Bitmap.createBitmap(px, f.w, f.h, Bitmap.Config.ARGB_8888)
        }
    }
}
