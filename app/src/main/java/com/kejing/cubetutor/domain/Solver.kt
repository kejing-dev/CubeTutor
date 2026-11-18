package com.kejing.cubetutor.domain

import java.util.PriorityQueue

/**
 * The seven-stage layer-by-layer tutor (design cubeSpec.solver, content.lessons L1–L7): teaching consistency over
 * shortness. Each stage is generated from the current cube with the stage's own method and formulas, using only
 * U D L R F and the whole-cube y (never B). Every step carries a short reason key ([Reasons]).
 *
 * The tutorial hold is white centre down (D), yellow up (design cubeSpec.holdConvention); [orient] brings a cube there.
 * Stage searches are small uniform-cost searches over the stage's "macros" (y turns, top turns, the stage formula), so
 * the path is exactly what the lesson text says to do (turn the top until …, then do the formula). Pure Kotlin.
 */
object Solver {
    /**
     * One move of the lesson: a notation token ("R", "U2", "y'"), its stage (1..7) and a reason key; and the group it
     * belongs to (user 2026-11-17: the "why" of the few moves around it, shown with every step): [group] = formula id
     * (Lessons.groups), [pos] of [len] moves in it, repetition [rep] (0-based) of [reps] (0 = until done).
     */
    data class Step(val move: String, val stage: Int, val reason: String, val group: String = "", val pos: Int = 0, val len: Int = 1, val rep: Int = 0, val reps: Int = 1)

    // --------------------------------------------------------------------------------------------- predicates
    // all relative to the centres; the lesson keeps white at D (y never moves D)

    private fun ok(c: Cube, facelets: IntArray): Boolean = facelets.all { c[it] == c[Facelets.center(Facelets.faceOf(it))] }

    fun edgeOk(c: Cube, slot: Int) = ok(c, Legality.edgeFacelets[slot])

    fun cornerOk(c: Cube, slot: Int) = ok(c, Legality.cornerFacelets[slot])

    fun crossDone(c: Cube) = (4..7).all { edgeOk(c, it) }

    fun bottomCorners(c: Cube) = (4..7).count { cornerOk(c, it) }

    fun firstLayer(c: Cube) = crossDone(c) && bottomCorners(c) == 4

    fun middleEdges(c: Cube) = (8..11).count { edgeOk(c, it) }

    fun firstTwo(c: Cube) = firstLayer(c) && middleEdges(c) == 4

    fun yellowCross(c: Cube) = (0..3).all { c[Legality.edgeFacelets[it][0]] == c.centerColor(0) }

    fun yellowAligned(c: Cube) = (0..3).all { edgeOk(c, it) }

    fun cornersHome(c: Cube) =
        (0..3).all { s -> Legality.cornerFacelets[s].map { c[it] }.toSet() == Legality.cornerFaces[s].map { c.centerColor(it) }.toSet() }

    /** White edges sitting in the top layer with white facing up (the daisy). */
    fun daisy(c: Cube): Int {
        val white = c.centerColor(3)
        return (0..3).count { c[Legality.edgeFacelets[it][0]] == white }
    }

    /** Is stage [k] (1..7) already done on [c]? */
    fun stageDone(c: Cube, k: Int): Boolean =
        when (k) {
            1 -> crossDone(c)
            2 -> firstLayer(c)
            3 -> firstTwo(c)
            4 -> firstTwo(c) && yellowCross(c)
            5 -> firstTwo(c) && yellowAligned(c)
            6 -> firstTwo(c) && yellowAligned(c) && cornersHome(c)
            else -> c.isSolved
        }

    /** The first stage not done yet (8 = solved). */
    fun firstOpenStage(c: Cube): Int = (1..7).firstOrNull { !stageDone(c, it) } ?: 8

    // --------------------------------------------------------------------------------------------- hold

    private val wholeTokens = listOf("y", "y'", "y2", "x", "x'", "x2", "z", "z'", "z2")

    /**
     * Whole-cube turns that bring the white centre to D and, when it is a side colour, [front] to F (design S08: "转到
     * 教程拿法"). Empty when already there; null when the cube has no white centre.
     */
    fun orient(c: Cube, front: CubeColor? = null): List<String>? {
        fun good(x: Cube) = x.centerColor(3) == CubeColor.WHITE && (front == null || x.centerColor(2) == front || x.centerColor(0) == front || x.centerColor(3) == front)
        if ((0 until 6).none { c.centerColor(it) == CubeColor.WHITE }) return null
        if (good(c)) return emptyList()
        val seen = HashSet<Cube>()
        val q = ArrayDeque<Pair<Cube, List<String>>>()
        q.add(c to emptyList())
        seen += c
        while (q.isNotEmpty()) {
            val (x, path) = q.removeFirst()
            for (t in wholeTokens) {
                val y = x.apply(t)
                if (!seen.add(y)) continue
                val p = path + t
                if (good(y)) return p
                if (p.size < 3) q.add(y to p)
            }
        }
        return null
    }

    // --------------------------------------------------------------------------------------------- search

    private class Macro(val tokens: List<String>, val role: String) {
        val where: IntArray = Perms.compose(tokens.flatMap { Moves.token(it) })
        val cost = tokens.size
    }

    private class Node(val cube: Cube, val path: List<Macro>, val cost: Int)

    /** Cheapest macro sequence (by move count) from [start] to a cube where [goal] holds; null within [maxCost]. */
    private fun search(start: Cube, macros: List<Macro>, maxCost: Int, maxNodes: Int = 400_000, goal: (Cube) -> Boolean): List<Macro>? {
        if (goal(start)) return emptyList()
        val q = PriorityQueue<Node>(compareBy { it.cost })
        q.add(Node(start, emptyList(), 0))
        val seen = HashMap<Cube, Int>()
        seen[start] = 0
        var expanded = 0
        while (q.isNotEmpty()) {
            val n = q.poll()
            if (goal(n.cube)) return n.path
            if (++expanded > maxNodes) return null
            val last = n.path.lastOrNull()
            for (m in macros) {
                // two top / two whole turns in a row are always one turn: skip (keeps the search small)
                if (last != null && last.role == m.role && (m.role == "U" || m.role == "y" || m.role.startsWith("face:"))) continue
                val c = n.cost + m.cost
                if (c > maxCost) continue
                val next = n.cube.permute(m.where)
                val prev = seen[next]
                if (prev != null && prev <= c) continue
                seen[next] = c
                q.add(Node(next, n.path + m, c))
            }
        }
        return null
    }

    private val U_TURNS = listOf(Macro(listOf("U"), "U"), Macro(listOf("U'"), "U"), Macro(listOf("U2"), "U"))
    private val Y_TURNS = listOf(Macro(listOf("y"), "y"), Macro(listOf("y'"), "y"), Macro(listOf("y2"), "y"))
    private val FACE_TURNS =
        listOf("U", "F", "R", "L", "D").flatMap { f -> listOf(f, "$f'", "${f}2").map { Macro(listOf(it), "face:$f") } }

    const val SEXY = "R U R' U'"
    const val RIGHT_BELT = "U R U' R' U' F' U F"
    const val LEFT_BELT = "U' L' U L U F U' F'"
    const val YELLOW_CROSS = "F R U R' U' F'"
    const val SUNE_SWAP = "R U R' U R U2 R' U"
    const val CORNER_CYCLE = "U R U' L' U R' U' L"
    const val TWIST = "R' D' R D"

    private fun formula(s: String, role: String) = Macro(Moves.tokens(s), role)

    private val L2_MACROS = U_TURNS + Y_TURNS + (1..5).map { k -> Macro((1..k).flatMap { Moves.tokens(SEXY) }, "T$k") }
    private val L3_MACROS = U_TURNS + Y_TURNS + listOf(formula(RIGHT_BELT, "RB"), formula(LEFT_BELT, "LB"))
    private val L4_MACROS = U_TURNS + listOf(formula(YELLOW_CROSS, "A"))
    private val L5_MACROS = U_TURNS + Y_TURNS + listOf(formula(SUNE_SWAP, "A"))
    private val L6_MACROS = Y_TURNS + listOf(formula(CORNER_CYCLE, "A"))

    /** Design limits per stage (cubeSpec.solver.limits). */
    val LIMIT = mapOf(1 to 60, 2 to 100, 3 to 80, 4 to 60, 5 to 60, 6 to 60, 7 to 60)

    // --------------------------------------------------------------------------------------------- stages

    /**
     * The steps of stage [k] from [cube] (white at D). Empty when the stage is already done. Throws when the cube is not
     * in the tutorial hold or not solvable (the caller checked legality first).
     */
    fun stage(cube: Cube, k: Int): List<Step> {
        require(cube.centerColor(3) == CubeColor.WHITE || (0 until 6).none { cube.centerColor(it) == CubeColor.WHITE }) { "white must be at D" }
        if (stageDone(cube, k)) return emptyList()
        // a stage keeps the ones before it: without them its search can never succeed and runs out of memory (headset
        // ANR 2026-11-18: stage 2 planned on a cube whose cross was one move short)
        check(firstOpenStage(cube) >= k) { "stage $k needs stages 1..${k - 1} done (first open ${firstOpenStage(cube)})" }
        return when (k) {
            1 -> cross(cube)
            2 -> pieces(cube, 2, L2_MACROS, 100, { c -> crossDone(c) }, ::bottomCorners)
            3 -> pieces(cube, 3, L3_MACROS, 60, ::firstLayer, ::middleEdges)
            4 -> whole(cube, 4, L4_MACROS, 40) { c -> firstTwo(c) && yellowCross(c) }
            5 -> whole(cube, 5, L5_MACROS, 50) { c -> firstTwo(c) && yellowAligned(c) }
            6 -> whole(cube, 6, L6_MACROS, 40) { c -> firstTwo(c) && yellowAligned(c) && cornersHome(c) }
            7 -> twist(cube)
            else -> emptyList()
        }
    }

    /** All stages from the first open one: what "跟着复原" plays (tests run 1000 scrambles through it). */
    fun solve(cube: Cube): List<Step> {
        val out = ArrayList<Step>()
        var c = cube
        for (k in 1..7) {
            val s = stage(c, k)
            out += s
            c = c.apply(s.joinToString(" ") { it.move })
        }
        return out
    }

    // L1: daisy (white edges up around the yellow centre), then each edge: top turn until its side colour meets the
    // front centre, F2 down; y to the next side (design lessons L1.method)
    private fun cross(start: Cube): List<Step> {
        val out = ArrayList<Step>()
        var c = start
        var guard = 0
        while (daisy(c) < 4) {
            val have = daisy(c)
            val path = search(c, FACE_TURNS, 7, 300_000) { x -> daisy(x) > have } ?: error("daisy search failed from ${c.code()}")
            val tokens = path.flatMap { it.tokens }
            tokens.forEachIndexed { i, t ->
                val reason = when {
                    i == tokens.lastIndex -> "L1.ring"
                    t.startsWith("U") -> "L1.room"
                    else -> "L1.lift"
                }
                out += Step(t, 1, reason, "DAISY", i, tokens.size)
            }
            c = c.apply(tokens.joinToString(" "))
            check(++guard < 8) { "daisy loop" }
        }
        for (k in 0 until 4) {
            val front = c.centerColor(2)
            val white = c.centerColor(3)
            val turns = listOf("", "U", "U2", "U'").first { u ->
                val x = if (u.isEmpty()) c else c.apply(u)
                x[Legality.edgeFacelets[1][0]] == white && x[Legality.edgeFacelets[1][1]] == front
            }
            val len = if (turns.isNotEmpty()) 2 else 1
            if (turns.isNotEmpty()) {
                out += Step(turns, 1, "L1.align", "DOWN", 0, len)
                c = c.apply(turns)
            }
            out += Step("F2", 1, "L1.down", "DOWN", len - 1, len)
            c = c.apply("F2")
            if (k < 3) {
                out += Step("y", 1, "y.next", "Y", 0, 1)
                c = c.apply("y")
            }
        }
        check(crossDone(c)) { "cross not done" }
        return out
    }

    // L2 / L3: one piece at a time; the last formula of each search is the insert, earlier ones take a stuck piece out
    private fun pieces(start: Cube, k: Int, macros: List<Macro>, maxCost: Int, keep: (Cube) -> Boolean, count: (Cube) -> Int): List<Step> {
        val out = ArrayList<Step>()
        var c = start
        while (count(c) < 4) {
            val have = count(c)
            val path = search(c, macros, maxCost) { x -> keep(x) && count(x) > have } ?: error("L$k search failed from ${c.code()}")
            val lastFormula = path.indexOfLast { it.role != "U" && it.role != "y" }
            path.forEachIndexed { i, m ->
                m.tokens.forEachIndexed { j, t -> out += grouped(Step(t, k, reasonFor(k, m, i == lastFormula, j, path, i)), k, m, j) }
            }
            c = c.apply(path.flatMap { it.tokens }.joinToString(" "))
        }
        return out
    }

    private fun whole(start: Cube, k: Int, macros: List<Macro>, maxCost: Int, goal: (Cube) -> Boolean): List<Step> {
        val path = search(start, macros, maxCost, goal = goal) ?: error("L$k search failed from ${start.code()}")
        val lastFormula = path.indexOfLast { it.role == "A" }
        val out = ArrayList<Step>()
        path.forEachIndexed { i, m -> m.tokens.forEachIndexed { j, t -> out += grouped(Step(t, k, reasonFor(k, m, i == lastFormula, j, path, i)), k, m, j) } }
        return out
    }

    /** The group of move [j] of macro [m] in stage [k] (Lessons.groups). */
    private fun grouped(st: Step, k: Int, m: Macro, j: Int): Step =
        when {
            m.role == "U" -> st.copy(group = "U", pos = 0, len = 1)
            m.role == "y" -> st.copy(group = "Y", pos = 0, len = 1)
            m.role.startsWith("T") -> st.copy(group = "SEXY", pos = j % 4, len = 4, rep = j / 4, reps = m.role.drop(1).toInt())
            m.role == "RB" || m.role == "LB" -> st.copy(group = m.role, pos = j, len = m.tokens.size)
            k == 4 -> st.copy(group = "YC", pos = j, len = m.tokens.size)
            k == 5 -> st.copy(group = "SWAP", pos = j, len = m.tokens.size)
            k == 6 -> st.copy(group = "CYCLE", pos = j, len = m.tokens.size)
            else -> st
        }

    private fun reasonFor(k: Int, m: Macro, lastFormula: Boolean, j: Int, path: List<Macro>, i: Int): String =
        when (k) {
            2 -> when {
                m.role == "y" -> "y.slot"
                m.role == "U" -> "L2.above"
                !lastFormula -> "L2.out"
                else -> listOf("L2.lift", "L2.in", "L2.back", "L2.top")[j % 4]
            }
            3 -> when {
                m.role == "y" -> "y.next"
                m.role == "U" -> "L3.align"
                !lastFormula -> "L3.out"
                else -> listOf("L3.hide", "L3.room", "L3.room", "L3.back", "L3.home", "L3.home", "L3.home", "L3.home")[j]
            }
            4 -> when (m.role) {
                "U" -> "L4.shape"
                else -> if (j == 0) "L4.down" else if (j == 5) "L4.up" else "L4.swap"
            }
            5 -> when {
                m.role == "A" -> "L5.swap"
                i > path.indexOfLast { it.role == "A" } -> "L5.last"
                else -> "L5.place"
            }
            else -> when (m.role) {
                "y" -> "L6.place"
                else -> "L6.cycle"
            }
        }

    // L7: hold still; R' D' R D until the front-right-top corner shows yellow up, then only the top turns (design L7)
    private fun twist(start: Cube): List<Step> {
        val out = ArrayList<Step>()
        var c = start
        val urf = Legality.cornerFacelets[0][0]
        for (k in 0 until 4) {
            var n = 0
            while (c[urf] != c.centerColor(0)) {
                Moves.tokens(TWIST).forEachIndexed { j, t -> out += Step(t, 7, "L7.twist", "TWIST", j, 4, n, 0) }
                c = c.apply(TWIST)
                check(++n <= 6) { "twist loop" }
            }
            if (k < 3) {
                out += Step("U", 7, "L7.next", "U", 0, 1)
                c = c.apply("U")
            }
        }
        val last = listOf("", "U", "U2", "U'").first { u -> (if (u.isEmpty()) c else c.apply(u)).isSolved }
        if (last.isNotEmpty()) out += Step(last, 7, "L7.last", "U", 0, 1)
        return out
    }
}

/** Random scrambles (design content.freePlay): no face twice in a row, no "U D U" style cancellation. */
object Scrambler {
    private val faces = "UDRLFB"
    private val axisOf = mapOf('U' to 0, 'D' to 0, 'R' to 1, 'L' to 1, 'F' to 2, 'B' to 2)

    fun make(n: Int, seed: Long): List<String> {
        val rnd = java.util.Random(seed)
        val out = ArrayList<String>()
        while (out.size < n) {
            val f = faces[rnd.nextInt(6)]
            val prev = out.lastOrNull()?.get(0)
            val prev2 = out.getOrNull(out.size - 2)?.get(0)
            if (f == prev) continue
            if (prev != null && prev2 != null && axisOf[f] == axisOf[prev] && axisOf[prev] == axisOf[prev2]) continue
            out += f + listOf("", "'", "2")[rnd.nextInt(3)]
        }
        return out
    }
}
