package com.kejing.cubetutor.domain.scan

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds the 3 x 3 sticker grid of the face held towards the camera (design cameraCapability.route.classifier ①②).
 * Port of the idea behind SpatialML-Samples/rubics_cube ScanManager (CPU-side, no model), with one change the design
 * asks for: the reference sample reads a fixed box in the middle of the frame, here the grid is *found* in the middle
 * 80 % so the user does not have to line the cube up with a frame.
 *
 * 1. Downsample to ~320 px wide (box filter).
 * 2. Stickers are the bright, evenly coloured patches between the dark plastic: pixels darker than 38 % of the 90th
 *    percentile are "plastic"; the rest are grown into regions of similar colour (difference to the region's running
 *    mean).
 * 3. Regions about sticker-shaped (fill ≥ 0.5, aspect 0.5–2) are candidates.
 * 4. For each candidate as the centre and two neighbours along two roughly perpendicular directions u, v, the nine
 *    points c + i·u + j·v (i, j ∈ −1..1) must all land on candidates of similar size; the best fit wins. Up to ±40°
 *    of tilt and some perspective work.
 * 5. Each cell's colour is the per-channel median of the middle 40 % of the sticker, at full resolution
 *    (the reference sample uses a 16-bin histogram mode over 2/3 of the cell: same intent, robust to glare edges).
 *
 * Pure Kotlin (JVM-tested); about 5–15 ms for a 640 x 480 frame.
 */
object GridFinder {
    private const val TARGET_W = 640
    private const val DARK_FRAC = 0.38f
    private const val DARK_MIN = 40
    private const val SIMILAR = 70
    private const val MIN_AREA = 14

    private class Blob(val cx: Float, val cy: Float, val area: Int) {
        val size: Float = sqrt(area.toFloat())
    }

    /**
     * The part of the camera frame where the alignment frame is (normalized 0..1; user addendum: detect only inside the
     * box). Default until the headset spike calibrates it: x 0.25–0.75, y 0.25–0.85 of the right-eye frame.
     */
    data class Roi(val x: Float, val y: Float, val w: Float, val h: Float) {
        companion object {
            val DEFAULT = Roi(0.25f, 0.25f, 0.5f, 0.6f)
            val FULL = Roi(0.1f, 0.1f, 0.8f, 0.8f)
        }
    }

    /** How the found grid sits: too small (far), turned (tilted), or fine. */
    enum class Quality { FAR, TILTED, OK }

    fun quality(g: Grid, frameW: Int, roi: Roi = Roi.DEFAULT): Quality {
        val a = g.cells[0]
        val b = g.cells[2]
        val tilt = Math.toDegrees(kotlin.math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).let { kotlin.math.abs(it) }
        return when {
            g.pitch * 3 < frameW * roi.w * 0.28f -> Quality.FAR
            tilt > 15.0 -> Quality.TILTED
            else -> Quality.OK
        }
    }

    fun find(f: ScanFrame, roi: Roi = Roi.FULL): Grid? {
        val ds = max(1, f.w / TARGET_W)
        val w = f.w / ds
        val h = f.h / ds
        if (w < 30 || h < 30) return null
        val r = IntArray(w * h)
        val g = IntArray(w * h)
        val b = IntArray(w * h)
        val n2 = ds * ds
        for (y in 0 until h) for (x in 0 until w) {
            var sr = 0
            var sg = 0
            var sb = 0
            for (yy in 0 until ds) for (xx in 0 until ds) {
                val i = ((y * ds + yy) * f.w + x * ds + xx) * 3
                sr += f.rgb[i].toInt() and 0xFF
                sg += f.rgb[i + 1].toInt() and 0xFF
                sb += f.rgb[i + 2].toInt() and 0xFF
            }
            val k = y * w + x
            r[k] = sr / n2
            g[k] = sg / n2
            b[k] = sb / n2
        }
        val x0 = (w * roi.x).toInt().coerceIn(0, w - 2)
        val x1 = (w * (roi.x + roi.w)).toInt().coerceIn(x0 + 1, w)
        val y0 = (h * roi.y).toInt().coerceIn(0, h - 2)
        val y1 = (h * (roi.y + roi.h)).toInt().coerceIn(y0 + 1, h)

        // brightness threshold from the ROI's own histogram (plastic is dark, stickers are not)
        val hist = IntArray(256)
        var total = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val k = y * w + x
            hist[max(r[k], max(g[k], b[k]))]++
            total++
        }
        var acc = 0
        var p90 = 255
        for (v in 0..255) {
            acc += hist[v]
            if (acc >= total * 0.9) {
                p90 = v
                break
            }
        }
        val dark = max(DARK_MIN, (p90 * DARK_FRAC).toInt())

        // region growing on similar colour
        val label = IntArray(w * h) { -1 }
        val queue = IntArray(w * h)
        val blobs = ArrayList<Blob>()
        val maxArea = (x1 - x0) * (y1 - y0) / 9
        var next = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val s = y * w + x
            if (label[s] >= 0 || max(r[s], max(g[s], b[s])) < dark) continue
            val id = next++
            var head = 0
            var tail = 0
            queue[tail++] = s
            label[s] = id
            var mr = r[s].toFloat()
            var mg = g[s].toFloat()
            var mb = b[s].toFloat()
            var n = 0
            var sx = 0L
            var sy = 0L
            var minX = x
            var maxX = x
            var minY = y
            var maxY = y
            var touches = false
            while (head < tail) {
                val p = queue[head++]
                val px = p % w
                val py = p / w
                n++
                sx += px
                sy += py
                mr += (r[p] - mr) / n
                mg += (g[p] - mg) / n
                mb += (b[p] - mb) / n
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
                for (d in 0 until 4) {
                    val qx = px + if (d == 0) 1 else if (d == 1) -1 else 0
                    val qy = py + if (d == 2) 1 else if (d == 3) -1 else 0
                    if (qx < x0 || qx >= x1 || qy < y0 || qy >= y1) {
                        touches = true
                        continue
                    }
                    val q = qy * w + qx
                    if (label[q] >= 0) continue
                    if (max(r[q], max(g[q], b[q])) < dark) continue
                    if (abs(r[q] - mr) + abs(g[q] - mg) + abs(b[q] - mb) > SIMILAR) continue
                    label[q] = id
                    queue[tail++] = q
                }
            }
            if (touches || n < MIN_AREA || n > maxArea) continue
            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val fill = n.toFloat() / (bw * bh)
            val aspect = bw.toFloat() / bh
            if (fill < 0.5f || aspect < 0.5f || aspect > 2f) continue
            blobs += Blob(sx.toFloat() / n, sy.toFloat() / n, n)
        }
        if (blobs.size < 9) return null
        val best = bestGrid(blobs) ?: return null
        val (cells, pitch) = best
        // sample at full resolution
        val half = max(1f, pitch * ds * 0.2f)
        val out =
            cells.map { bl ->
                val cx = (bl.cx + 0.5f) * ds
                val cy = (bl.cy + 0.5f) * ds
                sample(f, cx, cy, half)
            }
        return Grid(out, pitch * ds)
    }

    private fun sample(f: ScanFrame, cx: Float, cy: Float, half: Float): Cell {
        val xa = max(0, (cx - half).toInt())
        val xb = min(f.w - 1, (cx + half).toInt())
        val ya = max(0, (cy - half).toInt())
        val yb = min(f.h - 1, (cy + half).toInt())
        val rs = ArrayList<Int>()
        val gs = ArrayList<Int>()
        val bs = ArrayList<Int>()
        for (y in ya..yb) for (x in xa..xb) {
            rs += f.r(x, y)
            gs += f.g(x, y)
            bs += f.b(x, y)
        }
        return Cell(cx, cy, median(rs), median(gs), median(bs))
    }

    private fun median(v: ArrayList<Int>): Int {
        if (v.isEmpty()) return 0
        v.sort()
        return v[v.size / 2]
    }

    /** The best 3 x 3 lattice through the candidates: cells row-major (top row first, left to right) and the pitch. */
    private fun bestGrid(blobs: List<Blob>): Pair<List<Blob>, Float>? {
        var bestScore = Float.MAX_VALUE
        var best: List<Blob>? = null
        var bestPitch = 0f
        for (c in blobs) {
            val near =
                blobs.filter { it !== c && dist(it, c) < 3.4f * c.size && it.area > 0.35f * c.area && it.area < 2.8f * c.area }
            if (near.size < 8) continue
            val ring = near.filter { val d = dist(it, c); d > 0.9f * c.size && d < 2.4f * c.size }.sortedBy { dist(it, c) }.take(10)
            for (i in ring.indices) for (j in i + 1 until ring.size) {
                val ux = ring[i].cx - c.cx
                val uy = ring[i].cy - c.cy
                val vx = ring[j].cx - c.cx
                val vy = ring[j].cy - c.cy
                val lu = sqrt(ux * ux + uy * uy)
                val lv = sqrt(vx * vx + vy * vy)
                if (lu / lv < 0.7f || lu / lv > 1.43f) continue
                if (abs((ux * vx + uy * vy) / (lu * lv)) > 0.5f) continue
                val tol = 0.3f * min(lu, lv)
                val picked = arrayOfNulls<Blob>(9)
                var err = 0f
                var ok = true
                for (a in -1..1) {
                    for (bb in -1..1) {
                        val k = (bb + 1) * 3 + (a + 1)
                        if (a == 0 && bb == 0) {
                            picked[k] = c
                            continue
                        }
                        val px = c.cx + a * ux + bb * vx
                        val py = c.cy + a * uy + bb * vy
                        var hit: Blob? = null
                        var hd = tol
                        for (o in near) {
                            val d = sqrt((o.cx - px) * (o.cx - px) + (o.cy - py) * (o.cy - py))
                            if (d < hd) {
                                hd = d
                                hit = o
                            }
                        }
                        if (hit == null || picked.any { it === hit }) {
                            ok = false
                            break
                        }
                        picked[k] = hit
                        err += hd / min(lu, lv)
                    }
                    if (!ok) break
                }
                if (!ok) continue
                // similar sizes: penalise spread of the nine areas
                val areas = picked.map { it!!.area.toFloat() }
                val mean = areas.average().toFloat()
                val spread = areas.map { abs(it - mean) / mean }.average().toFloat()
                // pitch vs sticker size: stickers fill most of their cell
                val pitch = (lu + lv) / 2f
                val ratio = pitch / sqrt(mean)
                if (ratio < 0.95f || ratio > 2.3f) continue
                val score = err + spread
                if (score < bestScore) {
                    bestScore = score
                    best = order(picked.map { it!! }, ux, uy, vx, vy, c)
                    bestPitch = pitch
                }
            }
        }
        return best?.let { it to bestPitch }
    }

    /** Re-labels a lattice built along (u, v) so that columns run left→right and rows top→bottom in the image. */
    private fun order(lattice: List<Blob>, ux: Float, uy: Float, vx: Float, vy: Float, c: Blob): List<Blob> {
        // the axis that is more horizontal is the column axis
        val uHoriz = abs(ux) / sqrt(ux * ux + uy * uy) >= abs(vx) / sqrt(vx * vx + vy * vy)
        var ax = if (uHoriz) floatArrayOf(ux, uy) else floatArrayOf(vx, vy)
        var ay = if (uHoriz) floatArrayOf(vx, vy) else floatArrayOf(ux, uy)
        if (ax[0] < 0) ax = floatArrayOf(-ax[0], -ax[1])
        if (ay[1] < 0) ay = floatArrayOf(-ay[0], -ay[1])
        val out = ArrayList<Blob>(9)
        for (row in -1..1) for (col in -1..1) {
            val px = c.cx + col * ax[0] + row * ay[0]
            val py = c.cy + col * ax[1] + row * ay[1]
            out += lattice.minByOrNull { (it.cx - px) * (it.cx - px) + (it.cy - py) * (it.cy - py) }!!
        }
        return out
    }

    private fun dist(a: Blob, b: Blob): Float = sqrt((a.cx - b.cx) * (a.cx - b.cx) + (a.cy - b.cy) * (a.cy - b.cy))
}
