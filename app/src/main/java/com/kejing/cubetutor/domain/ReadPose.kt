package com.kejing.cubetutor.domain

/**
 * Design spatialLayout.cube.readPose (QA #1 q1-3, profile D17 补): while reading, the cube loosens instead of the
 * squares shrinking, and only the face being read can be hit. Pure numbers + the hit rule, unit-tested (⑦ ⑧).
 */
object ReadPose {
    const val CUBIE = 0.0653f
    const val SP = CUBIE + 0.002f

    /** Cubie pitch while reading (7.5 cm). */
    const val SP_READ = 0.075f

    /** Visible sticker = collider (5.5 cm, no padding). */
    const val TILE = 0.055f

    /** Handle collider = visible plate (5 cm); handles sit 6.67 cm apart. */
    const val HANDLE_HIT = 0.050f
    const val HANDLE_PITCH = 0.0667f

    /** Rig distance from the eye (RigLayout 0.60 m). */
    const val VIEW_M = 0.60f

    /** Sticker [i] has its collider on: reading → only the face being read; otherwise all. */
    fun hittable(readTarget: Int?, i: Int): Boolean = readTarget == null || Facelets.faceOf(i) == readTarget

    fun stickerGap(): Float = SP_READ - TILE

    fun handleGap(): Float = HANDLE_PITCH - HANDLE_HIT

    fun angleDeg(sizeM: Float, distM: Float = VIEW_M): Double = Math.toDegrees(2 * Math.atan((sizeM / 2.0) / distM))
}
