package com.kejing.cubetutor.domain.scan

import com.kejing.cubetutor.domain.Cube
import com.kejing.cubetutor.domain.CubeColor
import com.kejing.cubetutor.domain.Scrambler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** User addendum 2026-11-11: scan a few faces, infer the rest (unique → fill; several → ask a face; none → confirm). */
class InferenceTest {
    private fun cube(seed: Long) = Cube.solved().apply(Scrambler.make(25, seed).joinToString(" "))

    private fun known(c: Cube, faces: List<Int>): Array<CubeColor?> = Array(54) { if (it / 9 in faces) c[it] else null }

    /**
     * Five faces usually fix the sixth, but not always: when three edges of the missing face all show that face's colour
     * outward, their 3-cycles keep every parity → 3 candidates. Then the missing face is the one asked for.
     */
    @Test
    fun fiveFacesUsuallyDetermineTheSixth() {
        var unique = 0
        var total = 0
        for (seed in 1L..40L) {
            val c = cube(seed)
            for (missing in 0 until 6) {
                total++
                when (val r = Inference.infer(known(c, (0 until 6).filter { it != missing }))) {
                    is Inference.Result.Unique -> { unique++; assertEquals(c.code(), r.cube.code()); assertEquals(9, r.inferred.size) }
                    is Inference.Result.Ambiguous -> assertEquals(missing, r.askFace)
                    Inference.Result.None -> error("seed $seed missing $missing: none")
                }
            }
        }
        println("INFER five faces unique $unique / $total")
        assertTrue(unique * 10 >= total * 8)
        // six faces: always exactly one
        for (seed in 1L..40L) assertTrue(Inference.infer(known(cube(seed), (0 until 6).toList())) is Inference.Result.Unique)
    }

    @Test
    fun fourFacesOftenEnoughAndNeverWrong() {
        var unique = 0
        var total = 0
        val t0 = System.currentTimeMillis()
        for (seed in 1L..40L) {
            val c = cube(seed)
            // the read order's first four (F R B L) and a top + three sides (U F R B)
            for (faces in listOf(listOf(2, 1, 5, 4), listOf(0, 2, 1, 5))) {
                total++
                when (val r = Inference.infer(known(c, faces))) {
                    is Inference.Result.Unique -> { unique++; assertEquals(c.code(), r.cube.code()) }
                    is Inference.Result.Ambiguous -> assertTrue(r.askFace !in faces)
                    Inference.Result.None -> error("seed $seed $faces: the real cube was not found")
                }
            }
        }
        println("INFER four faces unique $unique / $total in ${System.currentTimeMillis() - t0} ms")
    }

    @Test
    fun askedFaceResolvesAmbiguity() {
        val needed = sortedMapOf<Int, Int>()
        for (seed in 1L..200L) {
            val c = cube(seed)
            val faces = mutableListOf(2, 1)
            var guard = 0
            while (true) {
                val r = Inference.infer(known(c, faces))
                if (r is Inference.Result.Unique) { assertEquals(c.code(), r.cube.code()); break }
                assertTrue("seed $seed $faces $r", r is Inference.Result.Ambiguous)
                faces += (r as Inference.Result.Ambiguous).askFace
                assertTrue(++guard < 6)
            }
            assertTrue("seed $seed needed ${faces.size}", faces.size <= 6)
            needed[faces.size] = (needed[faces.size] ?: 0) + 1
        }
        println("INFER faces needed (start F R, then the asked face): $needed")
    }

    @Test
    fun aMisreadStickerIsAContradiction() {
        val c = cube(7)
        val k = known(c, (0 until 6).toList())
        k[19] = CubeColor.SIX.first { it != c[19] && it != c[22] }
        assertEquals(Inference.Result.None, Inference.infer(k))
    }

    @Test
    fun centreFacesFollowTheScheme() {
        assertEquals(0, Inference.faceOf(CubeColor.YELLOW))
        assertEquals(2, Inference.faceOf(CubeColor.RED))
        assertEquals(3, Inference.faceOf(CubeColor.WHITE))
    }
}
