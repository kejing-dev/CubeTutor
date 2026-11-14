package com.kejing.cubetutor.platform

import android.util.Log
import com.kejing.cubetutor.domain.Quat4
import com.kejing.cubetutor.domain.Vec3
import java.util.Locale

/**
 * One line per event under the tag `CubeTutor`, with stable prefixes the QA scripts grep for:
 * `STATE`, `PLACE`, `PLAN`, `STAGE`, `TURN`, `INPUT`, `RAY`, `DRAG`, `WINDOW`, `INSTANCE`, `CMD`, ...
 */
object CtLog {
    const val TAG = "CubeTutor"

    fun i(line: String) {
        Log.i(TAG, line)
    }

    fun w(line: String, error: Throwable? = null) {
        Log.w(TAG, line, error)
    }

    fun e(line: String, error: Throwable? = null) {
        Log.e(TAG, line, error)
    }

    fun pos(v: Vec3?): String = if (v == null) "null" else String.format(Locale.US, "(%.3f, %.3f, %.3f)", v.x, v.y, v.z)

    fun quat(q: Quat4?): String = if (q == null) "null" else String.format(Locale.US, "q(%.4f, %.4f, %.4f, %.4f)", q.x, q.y, q.z, q.w)
}
