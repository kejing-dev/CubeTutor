package com.kejing.cubetutor.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Event sounds only (design sound, B24: no ambience). Turn clicks are CC0 recordings of a real cube (three variants in
 * rotation, ±4 % pitch, D51); UI cues are CC0 Kenney / two small synthesised chimes (build/sound-sources.md). One
 * instance per event at a time (a new one stops the last, profile D51 补), master volume 0–100 % with mute (D15).
 */
class Sound(private val context: Context) {
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val playing = HashMap<String, Int>()
    private var master = 0.8f
    private var muted = false
    private var turnIdx = 0
    private val rnd = java.util.Random()

    fun start() {
        if (pool != null) return
        val p =
            SoundPool.Builder().setMaxStreams(6)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build()
        pool = p
        for (n in NAMES) {
            runCatching { context.assets.openFd("sfx/$n.wav").use { fd -> ids[n] = p.load(fd, 1) } }.onFailure { CtLog.w("SOUND load $n failed: $it") }
        }
        CtLog.i("SOUND ready ${ids.keys.sorted()}")
    }

    fun release() {
        runCatching { pool?.release() }
        pool = null
        ids.clear()
        playing.clear()
    }

    fun setVolume(percent: Int, mute: Boolean) {
        master = percent.coerceIn(0, 100) / 100f
        muted = mute
        CtLog.i("SOUND volume=$percent muted=$mute")
    }

    private fun play(event: String, file: String, g: Float, rate: Float = 1f) {
        val p = pool ?: return
        val id = ids[file] ?: return
        val v = if (muted) 0f else (master * g).coerceIn(0f, 1f)
        if (v <= 0f) return
        playing.remove(event)?.let { runCatching { p.stop(it) } }
        val s = p.play(id, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
        if (s != 0) playing[event] = s
    }

    private fun jitter(pct: Float) = 1f + (rnd.nextFloat() * 2f - 1f) * pct

    /** A layer turn: the next of three recorded clicks, ±4 % pitch. */
    fun turn() {
        turnIdx = (turnIdx + 1) % 3
        play("turn", listOf("turn", "turn_2", "turn_3")[turnIdx], 1f, jitter(0.04f))
    }

    fun whole() = play("turn", "whole", 0.9f, jitter(0.03f))

    fun select() = play("select", "select", 0.7f)

    fun correct() = play("cue", "correct", 0.8f)

    fun nudge() = play("cue", "nudge", 0.7f)

    fun stageDone() = play("cue", "stage", 0.8f)

    fun solved() = play("cue", "solved", 0.85f)

    fun scanGot() = play("turn", "turn_2", 0.6f, 1.15f)

    companion object {
        val NAMES = listOf("turn", "turn_2", "turn_3", "whole", "select", "correct", "nudge", "stage", "solved")
    }
}
