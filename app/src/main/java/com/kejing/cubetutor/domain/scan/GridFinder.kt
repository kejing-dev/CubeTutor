package com.kejing.cubetutor.domain.scan

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Finds the 3 x 3 grid of the face held towards the camera (design cameraCapability.route.classifier ①②).
 * Port of the idea behind SpatialML-Samples/rubics_cube ScanManager (CPU-side, no model); here the grid is *found*
 * inside the alignment frame's ROI instead of read from a fixed box.
 *
 * Works for both kinds of cube:
 * - stickers on a black body (wide dark gaps), and
 * - stickerless cubes (coloured plastic tiles, headset spike 2026-11-17): the seams are ~1 px and only a little darker,
 *   and two neighbouring tiles of the same colour read as one region.
 *
 * 1. Downsample to ≤ 640 px wide (box filter).
 * 2. Separators: pixels darker than 38 % of the ROI's 90th percentile (plastic), plus *seams*: pixels clearly darker
 *    than both pixels 1–2 px away horizontally or vertically (a valley). Tiles are grown into regions of similar colour
 *    between the separators.
 * 3. Regions about tile-shaped (fill ≥ 0.5, aspect 0.5–2) are single candidates; all other closed regions (not touching
 *    the ROI edge, not too big) may still be several tiles merged.
 * 4. A lattice c + i·u + j·v comes from one single tile and two single neighbours along two roughly perpendicular
 *    directions, with that tile at any of the nine positions. All nine points must land in closed regions; a region
 *    holding k points must be about k tiles big; one step outside the face must not be in the same region as the edge
 *    cell beside it (so the lattice cannot slide around inside a bigger surface). Fewest merged cells, best fit wins.
 * 5. Each cell's colour is the per-channel median of the middle 40 % of the cell, at full resolution, sampled at the
 *    lattice point.
 *
 * Pure Kotlin (JVM-tested); about 10–40 ms for a 640 x 480 frame.
 */
object GridFinder {
    private const val TARGET_W = 640
    private const val DARK_FRAC = 0.38f
    private const val DARK_MIN = 40
    private const val SIMILAR = 32
    private const val MIN_AREA = 14

    /** A seam pixel is this much darker than both sides (fraction of the side, and at least [SEAM_MIN] levels). */
    private const val SEAM_FRAC = 0.10f
    private const val SEAM_MIN = 7

    /** A colour edge: summed |ΔR|+|ΔG|+|ΔB| across 2 px above this. */
    private const val EDGE = 36

    private class Blob(val id: Int, val cx: Float, val cy: Float, val area: Int) {
        val size: Float = sqrt(area.toFloat())
    }

    /**
     * The part of the camera frame where the alignment frame is (normalized 0..1; user addendum: detect only inside the
     * box). Default until calibrated: x 0.25–0.75, y 0.25–0.85 of the right-eye frame (spike 2026-11-17: a cube held
     * at the frame sat around x 0.37–0.52, y 0.52–0.68, inside).
     */
    data class Roi(val x: Float, val y: Float, val w: Float, val h: Float) {
        companion object {
            val DEFAULT = Roi(0.25f, 0.25f, 0.5f, 0.6f)

            /**
             * Where the reader looks (live run 2026-11-17): the cube lands anywhere from x 0.2 to 0.6 depending on the
             * hand and the distance (camera ≠ eye), and a face cut by the box was read shifted. The alignment frame
             * only guides; detection runs on the middle 80 %.
             */
            val FULL = Roi(0.1f, 0.1f, 0.8f, 0.8f)
        }
    }

    /** How the found grid sits: too small (far), turned (tilted), or fine. */
    enum class Quality { FAR, TILTED, OK }

    fun quality(g: Grid, frameW: Int, roi: Roi = Roi.DEFAULT): Quality =
        when {
            g.pitch * 3 < frameW * 0.08f -> Quality.FAR // spike: a cube held at the frame is ~60–75 px of 640
            !squareOn(g) -> Quality.TILTED
            else -> Quality.OK
        }

    /**
     * The face is held square to the camera (user 2026-11-17: a face still turning must not be taken). Rows run within
     * [MAX_TILT] of level, rows and columns are at right angles (±[MAX_SKEW]), and the face is not squeezed sideways
     * (being turned about the vertical axis) — looking down at the hands may squeeze it vertically.
     */
    fun squareOn(g: Grid): Boolean {
        val c = g.cells
        var hx = 0f
        var hy = 0f
        var vx = 0f
        var vy = 0f
        for (k in 0 until 3) {
            hx += c[k * 3 + 2].x - c[k * 3].x
            hy += c[k * 3 + 2].y - c[k * 3].y
            vx += c[6 + k].x - c[k].x
            vy += c[6 + k].y - c[k].y
        }
        val lh = sqrt(hx * hx + hy * hy)
        val lv = sqrt(vx * vx + vy * vy)
        if (lh < 1f || lv < 1f) return false
        val tilt = Math.toDegrees(kotlin.math.atan2(abs(hy).toDouble(), abs(hx).toDouble()))
        val skew = Math.toDegrees(kotlin.math.acos(((hx * vx + hy * vy) / (lh * lv)).toDouble().coerceIn(-1.0, 1.0))) - 90.0
        val ratio = lh / lv
        return tilt <= MAX_TILT && abs(skew) <= MAX_SKEW && ratio >= MIN_WIDTH && ratio <= MAX_WIDTH
    }

    private const val MAX_TILT = 20.0
    private const val MAX_SKEW = 15.0

    /** Width / height of the face in the picture: < 0.8 = turned sideways; up to 1.7 = seen from above (spike ~1.38). */
    private const val MIN_WIDTH = 0.8f
    private const val MAX_WIDTH = 1.7f

    /** Diagnostics of the last [find] (headset tuning; logged with the frame dumps). */
    class Stats {
        var dark = 0
        var seams = 0
        var regions = 0
        var touches = 0
        var small = 0
        var big = 0
        var shape = 0
        var merged = 0
        val blobs = ArrayList<FloatArray>() // cx, cy, area (downsampled px)
        override fun toString() =
            "dark=$dark seams=$seams regions=$regions touch=$touches small=$small big=$big shape=$shape cand=${blobs.size} mergedCells=$merged"
    }

    @Volatile var lastStats = Stats()
        private set

    fun find(f: ScanFrame, roi: Roi = Roi.FULL): Grid? {
        val st = Stats()
        lastStats = st
        val ds = max(1, f.w / TARGET_W)
        val w = f.w / ds
        val h = f.h / ds
        if (w < 30 || h < 30) return null
        val r = IntArray(w * h)
        val g = IntArray(w * h)
        val b = IntArray(w * h)
        val v = IntArray(w * h)
        val n2 = ds * ds
        var tr = 0L
        var tg = 0L
        var tb = 0L
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
            v[k] = max(r[k], max(g[k], b[k]))
            tr += r[k]
            tg += g[k]
            tb += b[k]
        }
        // gray-world white balance over the whole frame (spike 2026-11-17: warm office light turned blue to grey)
        val avg = (tr + tg + tb) / 3f
        val gain = floatArrayOf(gainOf(avg, tr), gainOf(avg, tg), gainOf(avg, tb))
        val x0 = (w * roi.x).toInt().coerceIn(0, w - 2)
        val x1 = (w * (roi.x + roi.w)).toInt().coerceIn(x0 + 1, w)
        val y0 = (h * roi.y).toInt().coerceIn(0, h - 2)
        val y1 = (h * (roi.y + roi.h)).toInt().coerceIn(y0 + 1, h)

        // brightness threshold from the ROI's own histogram (plastic is dark, tiles are not)
        val hist = IntArray(256)
        var total = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            hist[v[y * w + x]]++
            total++
        }
        var acc = 0
        var p90 = 255
        for (lv in 0..255) {
            acc += hist[lv]
            if (acc >= total * 0.9) {
                p90 = lv
                break
            }
        }
        val dark = max(DARK_MIN, (p90 * DARK_FRAC).toInt())
        st.dark = dark

        // separators: dark plastic, or a seam (valley 1–2 px wide in x or y)
        val sep = BooleanArray(w * h)
        for (y in y0 until y1) for (x in x0 until x1) {
            val k = y * w + x
            val c = v[k]
            if (c < dark) {
                sep[k] = true
                continue
            }
            var seam = false
            for (d in 1..2) {
                if (x - d >= 0 && x + d < w) {
                    val side = min(v[k - d], v[k + d])
                    if (side - c >= max(SEAM_MIN.toFloat(), side * SEAM_FRAC)) seam = true
                }
                if (y - d >= 0 && y + d < h) {
                    val side = min(v[k - d * w], v[k + d * w])
                    if (side - c >= max(SEAM_MIN.toFloat(), side * SEAM_FRAC)) seam = true
                }
                if (seam) break
            }
            // colour edge: a boundary between two tiles of different colours (or tile and background)
            if (!seam && x >= 1 && x + 1 < w && y >= 1 && y + 1 < h) {
                val ex = abs(r[k + 1] - r[k - 1]) + abs(g[k + 1] - g[k - 1]) + abs(b[k + 1] - b[k - 1])
                val ey = abs(r[k + w] - r[k - w]) + abs(g[k + w] - g[k - w]) + abs(b[k + w] - b[k - w])
                if (max(ex, ey) > EDGE) seam = true
            }
            if (seam) {
                sep[k] = true
                st.seams++
            }
        }

        // region growing on similar colour between the separators
        val label = IntArray(w * h) { -1 }
        val queue = IntArray(w * h)
        val singles = ArrayList<Blob>()
        val closed = HashMap<Int, Blob>() // every closed region (single tiles and merged ones)
        val maxArea = (x1 - x0) * (y1 - y0) / 4
        val maxSingle = (x1 - x0) * (y1 - y0) / 9
        var next = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val s = y * w + x
            if (label[s] >= 0 || sep[s]) continue
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
                    if (label[q] >= 0 || sep[q]) continue
                    if (abs(r[q] - mr) + abs(g[q] - mg) + abs(b[q] - mb) > SIMILAR) continue
                    label[q] = id
                    queue[tail++] = q
                }
            }
            st.regions++
            if (touches) {
                st.touches++
                continue
            }
            if (n < MIN_AREA) {
                st.small++
                continue
            }
            if (n > maxArea) {
                st.big++
                continue
            }
            val blob = Blob(id, sx.toFloat() / n, sy.toFloat() / n, n)
            closed[id] = blob
            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val fill = n.toFloat() / (bw * bh)
            val aspect = bw.toFloat() / bh
            if (n > maxSingle || fill < 0.5f || aspect < 0.5f || aspect > 2f) {
                st.shape++
                continue
            }
            singles += blob
            st.blobs += floatArrayOf(blob.cx, blob.cy, n.toFloat())
        }
        if (singles.size < 3) return null
        val fit = bestGrid(singles, closed, label, w, h) ?: return null
        st.merged = fit.merged
        val half = max(1f, fit.pitch * ds * 0.2f)
        val out = fit.points.map { (px, py) -> balanced(sample(f, (px + 0.5f) * ds, (py + 0.5f) * ds, half), gain) }
        return Grid(out, fit.pitch * ds)
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

    private fun gainOf(avg: Float, channel: Long): Float = if (channel <= 0) 1f else (avg / channel).coerceIn(0.5f, 2.5f)

    /**
     * White-balanced and brightness-normalised (brightest channel = [NORM]): tiles are told apart by colour, not by how
     * much light falls on them (a dim blue is not a grey / white).
     */
    private fun balanced(c: Cell, gain: FloatArray): Cell {
        val rr = c.r * gain[0]
        val gg = c.g * gain[1]
        val bb = c.b * gain[2]
        val m = max(rr, max(gg, bb))
        if (m < 1f) return c
        val k = NORM / m
        return Cell(c.x, c.y, (rr * k).roundToInt().coerceIn(0, 255), (gg * k).roundToInt().coerceIn(0, 255), (bb * k).roundToInt().coerceIn(0, 255))
    }

    private const val NORM = 220f

    /** Lengths of the perpendicular axis tried against a known one (foreshortening up to ~0.6). */
    private val PERP = floatArrayOf(0.6f, 0.7f, 0.8f, 0.9f, 1f, 1.12f, 1.25f, 1.4f, 1.6f)

    private fun median(v: ArrayList<Int>): Int {
        if (v.isEmpty()) return 0
        v.sort()
        return v[v.size / 2]
    }

    private class Fit(val points: List<Pair<Float, Float>>, val pitch: Float, val merged: Int)

    /**
     * The best 3 x 3 lattice: points row-major (top row first, left to right, in the image), the pitch, and how many
     * of the nine points sit in merged regions.
     */
    private fun bestGrid(singles: List<Blob>, closed: Map<Int, Blob>, label: IntArray, w: Int, h: Int): Fit? {
        fun regionAt(px: Float, py: Float): Int {
            val x = px.roundToInt()
            val y = py.roundToInt()
            if (x < 0 || y < 0 || x >= w || y >= h) return -1
            val id = label[y * w + x]
            if (id >= 0 && closed.containsKey(id)) return id
            // a lattice point may fall on a seam: take the region most of its 3 x 3 neighbourhood belongs to
            val votes = HashMap<Int, Int>()
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx
                val yy = y + dy
                if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                val l = label[yy * w + xx]
                if (l >= 0 && closed.containsKey(l)) votes[l] = (votes[l] ?: 0) + 1
            }
            return votes.maxByOrNull { it.value }?.takeIf { it.value >= 3 }?.key ?: -1
        }

        var bestScore = Float.MAX_VALUE
        var best: Fit? = null
        val tried = HashSet<Long>()
        fun consider(c: Blob, ux: Float, uy: Float, vx: Float, vy: Float, tile: Float) {
            val lu = sqrt(ux * ux + uy * uy)
            val lv = sqrt(vx * vx + vy * vy)
            if (lu / lv < 0.6f || lu / lv > 1.67f) return
            if (abs((ux * vx + uy * vy) / (lu * lv)) > 0.5f) return
            val pitch = (lu + lv) / 2f
            val ratio = pitch / sqrt(tile)
            if (ratio < 0.9f || ratio > 2.3f) return
            // c may be any of the nine cells (stickerless faces: the centre is often merged with a neighbour)
            for (pa in -1..1) for (pb in -1..1) {
                val ox = c.cx - pa * ux - pb * vx
                val oy = c.cy - pa * uy - pb * vy
                // the same lattice is reached from several tiles: skip repeats (rounded centre + axes)
                val key = (ox.roundToInt().toLong() shl 48) xor (oy.roundToInt().toLong() shl 32) xor
                    ((ux / 2).roundToInt().toLong() shl 24) xor ((uy / 2).roundToInt().toLong() shl 16) xor
                    ((vx / 2).roundToInt().toLong() shl 8) xor (vy / 2).roundToInt().toLong()
                if (!tried.add(key)) continue
                val score = tryLattice(ox, oy, ux, uy, vx, vy, tile, closed, ::regionAt) ?: continue
                if (score.first < bestScore) {
                    bestScore = score.first
                    best = Fit(ordered(ox, oy, ux, uy, vx, vy), pitch, score.second)
                }
            }
        }
        for (c in singles) {
            val near =
                singles
                    .filter { it !== c && it.area > 0.35f * c.area && it.area < 2.8f * c.area }
                    .filter { val d = dist(it, c); d > 0.8f * c.size && d < 3.6f * c.size }
                    .sortedBy { dist(it, c) }
                    .take(8)
            // ① two neighbours along two directions (stickers on a black body: every tile is separate)
            for (i in near.indices) for (j in i + 1 until near.size) {
                val tile = (c.area + near[i].area + near[j].area) / 3f
                consider(c, near[i].cx - c.cx, near[i].cy - c.cy, near[j].cx - c.cx, near[j].cy - c.cy, tile)
            }
            // ② one neighbour, 1 or 2 steps away; the other axis is its perpendicular at a few lengths
            //    (foreshortened when the face is seen from above; merged same-colour tiles leave few separate ones)
            for (o in near) {
                val tile = (c.area + o.area) / 2f
                for (n in 1..2) {
                    val ux = (o.cx - c.cx) / n
                    val uy = (o.cy - c.cy) / n
                    for (k in PERP) consider(c, ux, uy, -uy * k, ux * k, tile)
                }
            }
        }
        return best
    }

    /** Score (lower is better) and merged-cell count of the lattice centred at (ox, oy), or null when it does not fit. */
    private fun tryLattice(
        ox: Float, oy: Float, ux: Float, uy: Float, vx: Float, vy: Float, tile: Float,
        closed: Map<Int, Blob>, regionAt: (Float, Float) -> Int,
    ): Pair<Float, Int>? {
        val ids = IntArray(9)
        for (a in -1..1) for (bb in -1..1) {
            val id = regionAt(ox + a * ux + bb * vx, oy + a * uy + bb * vy)
            if (id < 0) return null
            ids[(bb + 1) * 3 + (a + 1)] = id
        }
        val counts = ids.groupBy { it }.mapValues { it.value.size }
        var err = 0f
        var merged = 0
        var spread = 0f
        for ((id, k) in counts) {
            val bl = closed[id]!!
            val per = bl.area / (k * tile)
            if (per < 0.55f || per > 1.7f) return null
            if (k > 1) {
                merged += k
                // the points sharing a region must be neighbours (two blues side by side), not two corners of one tile
                val pts = ids.indices.filter { ids[it] == id }
                for (p in pts) if (pts.none { q -> q != p && abs(q % 3 - p % 3) + abs(q / 3 - p / 3) == 1 }) return null
            }
            spread += abs(per - 1f)
        }
        // a merged region must not reach beyond the face: one step outside each edge cell is another region
        for (a in -1..1) for (bb in -1..1) {
            if (a == 0 && bb == 0) continue
            val id = ids[(bb + 1) * 3 + (a + 1)]
            if (counts[id] == 1) continue
            val oa = if (a == 0) 0 else 2 * a
            val ob = if (bb == 0) 0 else 2 * bb
            if (a != 0 && regionAt(ox + oa * ux + bb * vx, oy + oa * uy + bb * vy) == id) return null
            if (bb != 0 && regionAt(ox + a * ux + ob * vx, oy + a * uy + ob * vy) == id) return null
        }
        // the face must not go on past an edge: full-size tiles in line one step outside mean the lattice slid one
        // row / column onto a side face (live run 2026-11-17: a face cut by the old ROI was read shifted)
        for (side in 0 until 4) {
            var cont = 0
            for (k in -1..1) {
                val (a, bb) = when (side) { 0 -> -2 to k; 1 -> 2 to k; 2 -> k to -2; else -> k to 2 }
                val px = ox + a * ux + bb * vx
                val py = oy + a * uy + bb * vy
                val id = regionAt(px, py)
                if (id < 0 || id in ids) continue
                val bl = closed[id]!!
                val per = bl.area / tile
                val off = sqrt((bl.cx - px) * (bl.cx - px) + (bl.cy - py) * (bl.cy - py)) / sqrt(tile)
                if (per in 0.7f..1.45f && off < 0.3f) cont++
            }
            if (cont >= 2) return null
        }
        // single cells: the lattice point must be near the tile's centroid
        var singles = 0
        for (a in -1..1) for (bb in -1..1) {
            val id = ids[(bb + 1) * 3 + (a + 1)]
            if (counts[id] != 1) continue
            val bl = closed[id]!!
            val px = ox + a * ux + bb * vx
            val py = oy + a * uy + bb * vy
            val e = sqrt((bl.cx - px) * (bl.cx - px) + (bl.cy - py) * (bl.cy - py)) / sqrt(tile)
            if (e > 0.45f) return null
            err += e
            singles++
        }
        if (singles < 3) return null
        return (err / singles + spread / counts.size + 0.06f * merged) to merged
    }

    /** The nine lattice points with columns left→right and rows top→bottom in the image. */
    private fun ordered(ox: Float, oy: Float, ux: Float, uy: Float, vx: Float, vy: Float): List<Pair<Float, Float>> {
        val uHoriz = abs(ux) / sqrt(ux * ux + uy * uy) >= abs(vx) / sqrt(vx * vx + vy * vy)
        var ax = if (uHoriz) floatArrayOf(ux, uy) else floatArrayOf(vx, vy)
        var ay = if (uHoriz) floatArrayOf(vx, vy) else floatArrayOf(ux, uy)
        if (ax[0] < 0) ax = floatArrayOf(-ax[0], -ax[1])
        if (ay[1] < 0) ay = floatArrayOf(-ay[0], -ay[1])
        val out = ArrayList<Pair<Float, Float>>(9)
        for (row in -1..1) for (col in -1..1) out += (ox + col * ax[0] + row * ay[0]) to (oy + col * ax[1] + row * ay[1])
        return out
    }

    private fun dist(a: Blob, b: Blob): Float = sqrt((a.cx - b.cx) * (a.cx - b.cx) + (a.cy - b.cy) * (a.cy - b.cy))
}
