package com.kejing.cubetutor.domain.scan

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater

/** Minimal PNG reader for the JVM tests (8-bit RGB / RGBA, all five filters): javax.imageio is not on the Android test classpath. */
object Png {
    fun read(f: File, t: Long = 0L): ScanFrame {
        val d = f.readBytes()
        var i = 8
        var w = 0
        var h = 0
        var ct = 0
        val idat = ByteArrayOutputStream()
        while (i < d.size) {
            val n = ((d[i].toInt() and 0xFF) shl 24) or ((d[i + 1].toInt() and 0xFF) shl 16) or ((d[i + 2].toInt() and 0xFF) shl 8) or (d[i + 3].toInt() and 0xFF)
            val type = String(d, i + 4, 4, Charsets.US_ASCII)
            if (type == "IHDR") {
                w = be(d, i + 8)
                h = be(d, i + 12)
                require(d[i + 16].toInt() == 8) { "bit depth" }
                ct = d[i + 17].toInt()
            } else if (type == "IDAT") idat.write(d, i + 8, n)
            i += 12 + n
        }
        val bpp = when (ct) { 2 -> 3; 6 -> 4; else -> error("colour type $ct") }
        val inf = Inflater().apply { setInput(idat.toByteArray()) }
        val raw = ByteArray((w * bpp + 1) * h)
        var got = 0
        while (got < raw.size && !inf.finished()) got += inf.inflate(raw, got, raw.size - got)
        val stride = w * bpp
        val cur = ByteArray(stride)
        val prev = ByteArray(stride)
        val rgb = ByteArray(w * h * 3)
        for (y in 0 until h) {
            val ft = raw[y * (stride + 1)].toInt()
            for (x in 0 until stride) {
                val v = raw[y * (stride + 1) + 1 + x].toInt() and 0xFF
                val a = if (x >= bpp) cur[x - bpp].toInt() and 0xFF else 0
                val b = prev[x].toInt() and 0xFF
                val c = if (x >= bpp) prev[x - bpp].toInt() and 0xFF else 0
                val o = when (ft) {
                    0 -> v
                    1 -> v + a
                    2 -> v + b
                    3 -> v + (a + b) / 2
                    else -> { val p = a + b - c; val pa = Math.abs(p - a); val pb = Math.abs(p - b); val pc = Math.abs(p - c); v + if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c }
                }
                cur[x] = o.toByte()
            }
            for (x in 0 until w) System.arraycopy(cur, x * bpp, rgb, (y * w + x) * 3, 3)
            System.arraycopy(cur, 0, prev, 0, stride)
        }
        return ScanFrame(w, h, rgb, t)
    }

    private fun be(d: ByteArray, i: Int) = ((d[i].toInt() and 0xFF) shl 24) or ((d[i + 1].toInt() and 0xFF) shl 16) or ((d[i + 2].toInt() and 0xFF) shl 8) or (d[i + 3].toInt() and 0xFF)
}
