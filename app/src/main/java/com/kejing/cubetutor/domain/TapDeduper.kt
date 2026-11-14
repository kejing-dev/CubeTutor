package com.kejing.cubetutor.domain

/**
 * One press = one tap (v0.1.2, user on the headset: 「点了魔方旁边的按钮点一下好像就取消了」). A controller trigger reached
 * the app twice — the system pointer's tap (board-input) and the app's own trigger-on-ray — and a handle tap toggles, so
 * the second copy un-picked it. The app's own trigger path is gone; this guard also drops any repeat of the same target
 * within [WINDOW_MS], whatever the source. A deliberate second tap (再点一次取消) comes later than that.
 */
class TapDeduper(private val windowMs: Long = WINDOW_MS) {
    private var lastKey: String? = null
    private var lastAt = Long.MIN_VALUE / 2

    /** True when this tap should be handled; false = the same press seen again. */
    fun accept(key: String, nowMs: Long): Boolean {
        val dup = key == lastKey && nowMs - lastAt in 0 until windowMs
        if (!dup) {
            lastKey = key
            lastAt = nowMs
        }
        return !dup
    }

    companion object {
        const val WINDOW_MS = 350L
    }
}
