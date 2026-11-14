package com.kejing.cubetutor.platform.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.kejing.cubetutor.platform.CtLog

/**
 * CAMERA runtime permission for the readback (design cameraCapability.permission). The Spatial launch activity is not
 * ours to override, so the answer is polled: granted → go; still not granted once the system dialog is gone (the
 * activity has focus again) or after 30 s → denied (S05). U2 (where the dialog shows up in Full Space) is a headset item.
 */
object CameraPermission {
    private val main = Handler(Looper.getMainLooper())
    private var waiting: ((Boolean) -> Unit)? = null

    fun granted(ctx: Context): Boolean = ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun request(ctx: Context, done: (Boolean) -> Unit) {
        val a = ctx as? Activity
        if (a == null) {
            done(false)
            return
        }
        waiting = done
        CtLog.i("CAM permission request")
        runCatching { a.requestPermissions(arrayOf(Manifest.permission.CAMERA), 4711) }.onFailure {
            CtLog.w("CAM permission request failed: $it")
            finish(false)
            return
        }
        val t0 = System.currentTimeMillis()
        var sawDialog = false
        val poll =
            object : Runnable {
                override fun run() {
                    if (waiting == null) return
                    if (granted(a)) return finish(true)
                    val focus = a.hasWindowFocus()
                    if (!focus) sawDialog = true
                    if ((sawDialog && focus) || System.currentTimeMillis() - t0 > 30_000L) return finish(false)
                    main.postDelayed(this, 300L)
                }
            }
        main.postDelayed(poll, 500L)
    }

    private fun finish(ok: Boolean) {
        CtLog.i("CAM permission granted=$ok")
        val w = waiting ?: return
        waiting = null
        w(ok)
    }
}
