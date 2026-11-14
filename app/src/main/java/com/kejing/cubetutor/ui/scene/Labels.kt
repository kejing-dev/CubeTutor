package com.kejing.cubetutor.ui.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.kejing.cubetutor.domain.Push

/**
 * Run-time bitmaps for the 3D small parts (design entities3d.layerHandle / dpadBar / tray, assets 小字牌: 运行时位图,
 * light text on a dark rounded card). Textures are uploaded once per (text, look) and kept by the kit.
 */
internal object Labels {
    private val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    enum class Look { NORMAL, SELECTED, CUE }

    /**
     * A handle face: [text] centred; NORMAL = dark plate with a [rowColor] frame, SELECTED = filled with the row colour,
     * dark text; CUE = the lesson's gold with dark text (pulses against the other two).
     */
    fun handle(text: String, rowColor: Int, look: Look, px: Int = 160): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = px * 0.16f
        val rect = RectF(0f, 0f, px.toFloat(), px.toFloat())
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.color = when (look) {
            Look.NORMAL -> HANDLE_BG
            Look.SELECTED -> rowColor
            Look.CUE -> GOLD
        }
        c.drawRoundRect(rect, r, r, bg)
        if (look == Look.NORMAL) {
            val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = px * 0.075f; color = rowColor }
            val inset = frame.strokeWidth / 2
            c.drawRoundRect(RectF(inset, inset, px - inset, px - inset), r - inset, r - inset, frame)
        }
        // selected / cue: dark ink on the row colour or gold (white on the row colours was only 2.2–2.8:1, D13)
        val ink = if (look == Look.NORMAL) INK_LIGHT else INK_DARK
        text(c, text, px / 2f, px / 2f, px * 0.84f, px * if (text.length <= 1) 0.5f else if (text.length <= 2) 0.36f else 0.26f, ink)
        return bmp
    }

    /** One arrow key of the bar: a filled triangle (left / right / up / down) or a turning arc (clockwise / counter). */
    fun arrow(kind: Push?, curved: Boolean, look: Look, px: Int = 160): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = px * 0.2f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (look == Look.CUE) GOLD else if (look == Look.SELECTED) SELECT_GREY else HANDLE_BG }
        c.drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), r, r, bg)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (look == Look.CUE) INK_DARK else INK_LIGHT }
        val m = px / 2f
        val s = px * 0.28f
        if (kind == null) return bmp
        if (!curved) {
            val p = Path()
            when (kind) {
                Push.LEFT -> { p.moveTo(m - s, m); p.lineTo(m + s * 0.8f, m - s); p.lineTo(m + s * 0.8f, m + s) }
                Push.RIGHT -> { p.moveTo(m + s, m); p.lineTo(m - s * 0.8f, m - s); p.lineTo(m - s * 0.8f, m + s) }
                Push.UP -> { p.moveTo(m, m - s); p.lineTo(m - s, m + s * 0.8f); p.lineTo(m + s, m + s * 0.8f) }
                Push.DOWN -> { p.moveTo(m, m + s); p.lineTo(m - s, m - s * 0.8f); p.lineTo(m + s, m - s * 0.8f) }
            }
            p.close()
            c.drawPath(p, ink)
        } else {
            // LEFT = counter-clockwise (↺), RIGHT = clockwise (↻), as you look at the front (design dpad: 前后 → ↺ ↻)
            val cw = kind == Push.RIGHT || kind == Push.DOWN
            val stroke = Paint(ink).apply { style = Paint.Style.STROKE; strokeWidth = px * 0.09f; strokeCap = Paint.Cap.ROUND }
            val oval = RectF(m - s, m - s, m + s, m + s)
            val start = if (cw) 200f else -20f
            val sweep = if (cw) 250f else -250f
            c.drawArc(oval, start, sweep, false, stroke)
            val end = Math.toRadians((start + sweep).toDouble())
            val ex = m + s * Math.cos(end).toFloat()
            val ey = m + s * Math.sin(end).toFloat()
            // tangent at the end in the direction of travel
            val dir = if (cw) 1f else -1f
            val tx = -Math.sin(end).toFloat() * dir
            val ty = Math.cos(end).toFloat() * dir
            val nx = -ty
            val ny = tx
            val h = px * 0.16f
            val p = Path()
            p.moveTo(ex + tx * h, ey + ty * h)
            p.lineTo(ex + nx * h * 0.9f, ey + ny * h * 0.9f)
            p.lineTo(ex - nx * h * 0.9f, ey - ny * h * 0.9f)
            p.close()
            c.drawPath(p, ink)
        }
        return bmp
    }

    /** A wide strip label (the bar's middle text, the tray): light text on the dark plate, shrunk to fit. */
    fun strip(text: String, pxW: Int, pxH: Int, bgColor: Int = HANDLE_BG, ink: Int = INK_LIGHT): Bitmap {
        val bmp = Bitmap.createBitmap(pxW, pxH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = pxH * 0.3f
        c.drawRoundRect(RectF(0f, 0f, pxW.toFloat(), pxH.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor })
        text(c, text, pxW / 2f, pxH / 2f, pxW * 0.92f, pxH * 0.46f, ink)
        return bmp
    }

    /** A test card for texture orientation (D10: 「F↑」 must read upright, not mirrored). */
    fun orientationTest(px: Int = 160): Bitmap = handle("F↑", INK_LIGHT, Look.NORMAL, px)

    private fun text(c: Canvas, text: String, cx: Float, cy: Float, maxW: Float, size: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.CENTER
            typeface = bold
            textSize = size
        }
        while (p.measureText(text) > maxW && p.textSize > 8f) p.textSize -= 2f
        val fm = p.fontMetrics
        c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, p)
    }

    // ARGB of the design colours (CtColors mirrors them for Compose; the 3D side draws on android.graphics)
    const val HANDLE_BG = 0xFF242A31.toInt() // design-style: fixed-figma-color
    const val SELECT_GREY = 0xFF3A4350.toInt() // design-style: fixed-figma-color
    const val GOLD = 0xFFF2B33D.toInt() // design-style: fixed-figma-color turnArc
    const val INK_LIGHT = 0xFFF4F4EF.toInt() // design-style: fixed-figma-color
    const val INK_DARK = 0xFF1B1B1F.toInt() // design-style: fixed-figma-color
    const val ROW = 0xFF7D93B2.toInt() // design-style: fixed-figma-color 行 蓝灰
    const val COL = 0xFFE8925A.toInt() // design-style: fixed-figma-color 列 暖橙
    const val DEPTH = 0xFF3FB5A8.toInt() // design-style: fixed-figma-color 前后 青绿
}
