package com.kejing.cubetutor.domain

/**
 * The 3×3 cube as 54 facelets in Kociemba URFDLB order (design cubeSpec.faceletOrder): U 0–8, R 9–17, F 18–26,
 * D 27–35, L 36–44, B 45–53; each face row by row from its top-left as you look at it (U's top = B, D's top = F).
 *
 * The state is in the DISPLAY frame (cube-local: +X right, +Y up, +Z towards the user, design cubeSpec.notation): a
 * whole-cube turn (y, x, z) permutes the facelets too, so the cube on the Stage never rotates as a whole and a handle
 * slot always means the same display layer (design spatialLayout.joystickMapping: "按展示视角算").
 *
 * Every turn is "these layers along this axis, a quarter turn by the right-hand rule" ([Turn]); notation moves map onto
 * it ([Moves]). Pure Kotlin, JVM-tested.
 */
object Facelets {
    const val U = 0
    const val R = 9
    const val F = 18
    const val D = 27
    const val L = 36
    const val B = 45

    /** Face letters in URFDLB order. */
    const val FACES = "URFDLB"

    /** Integer position (each coordinate -1..1) and outward normal of every facelet. */
    val pos: Array<IntArray>
    val normal: Array<IntArray>
    private val index = HashMap<Int, Int>()

    init {
        // face normal, "right" and "down" vectors as you look at the face in the Kociemba net
        val n = arrayOf(intArrayOf(0, 1, 0), intArrayOf(1, 0, 0), intArrayOf(0, 0, 1), intArrayOf(0, -1, 0), intArrayOf(-1, 0, 0), intArrayOf(0, 0, -1))
        val r = arrayOf(intArrayOf(1, 0, 0), intArrayOf(0, 0, -1), intArrayOf(1, 0, 0), intArrayOf(1, 0, 0), intArrayOf(0, 0, 1), intArrayOf(-1, 0, 0))
        val d = arrayOf(intArrayOf(0, 0, 1), intArrayOf(0, -1, 0), intArrayOf(0, -1, 0), intArrayOf(0, 0, -1), intArrayOf(0, -1, 0), intArrayOf(0, -1, 0))
        pos = Array(54) { IntArray(3) }
        normal = Array(54) { IntArray(3) }
        for (f in 0 until 6) for (row in 0 until 3) for (col in 0 until 3) {
            val i = f * 9 + row * 3 + col
            for (k in 0 until 3) {
                pos[i][k] = n[f][k] + (col - 1) * r[f][k] + (row - 1) * d[f][k]
                normal[i][k] = n[f][k]
            }
            index[key(pos[i], normal[i])] = i
        }
    }

    private fun key(p: IntArray, nn: IntArray): Int = ((p[0] + 1) * 9 + (p[1] + 1) * 3 + (p[2] + 1)) * 64 + ((nn[0] + 1) * 9 + (nn[1] + 1) * 3 + (nn[2] + 1))

    fun at(p: IntArray, nn: IntArray): Int = index[key(p, nn)] ?: error("no facelet at ${p.toList()} ${nn.toList()}")

    /** Face index 0..5 (URFDLB) of a facelet. */
    fun faceOf(i: Int): Int = i / 9

    fun center(face: Int): Int = face * 9 + 4

    /** Name used in debug commands and entity names: "F5" = F face, 5th sticker (1-based, Kociemba). */
    fun name(i: Int): String = "${FACES[i / 9]}${i % 9 + 1}"

    fun parse(s: String): Int? {
        if (s.length != 2) return null
        val f = FACES.indexOf(s[0].uppercaseChar())
        val k = s[1].digitToIntOrNull() ?: return null
        if (f < 0 || k !in 1..9) return null
        return f * 9 + k - 1
    }
}

enum class Axis(val v: Int) { X(0), Y(1), Z(2) }

/**
 * A quarter turn of [layers] (display coordinates along [axis], each -1, 0 or 1) by +90° ([dir] = +1) or -90° ([dir] =
 * -1) about the positive axis (right-hand rule). U = Y {+1} -1, R = X {+1} -1, F = Z {+1} -1 (design cubeSpec.notation).
 */
data class Turn(val axis: Axis, val layers: Set<Int>, val dir: Int) {
    init {
        require(dir == 1 || dir == -1) { "dir $dir" }
        require(layers.isNotEmpty() && layers.all { it in -1..1 }) { "layers $layers" }
    }

    val inverse: Turn
        get() = copy(dir = -dir)

    val whole: Boolean
        get() = layers.size == 3

    /** Facelet permutation: after the turn, facelet `perm[i]` holds what facelet `i` held. */
    val perm: IntArray by lazy { Perms.of(this) }

    override fun toString(): String = "${axis.name}${layers.sorted().joinToString(",", "{", "}")}${if (dir > 0) "+" else "-"}"
}

object Perms {
    private fun rot(v: IntArray, axis: Axis, dir: Int): IntArray {
        val (x, y, z) = Triple(v[0], v[1], v[2])
        return when (axis) {
            Axis.X -> if (dir > 0) intArrayOf(x, -z, y) else intArrayOf(x, z, -y)
            Axis.Y -> if (dir > 0) intArrayOf(z, y, -x) else intArrayOf(-z, y, x)
            Axis.Z -> if (dir > 0) intArrayOf(-y, x, z) else intArrayOf(y, -x, z)
        }
    }

    fun of(t: Turn): IntArray =
        IntArray(54) { i ->
            val p = Facelets.pos[i]
            if (p[t.axis.v] in t.layers) Facelets.at(rot(p, t.axis, t.dir), rot(Facelets.normal[i], t.axis, t.dir)) else i
        }

    /** Where each facelet ends up after [turns] in order (one array, so a macro costs one pass). */
    fun compose(turns: List<Turn>): IntArray {
        val where = IntArray(54) { it }
        for (t in turns) {
            val p = t.perm
            for (i in 0 until 54) where[i] = p[where[i]]
        }
        return where
    }

    /** Rotates a point (or direction) the same way, for the scene's turn animation and the tests. */
    fun rotatePoint(v: IntArray, axis: Axis, dir: Int): IntArray = rot(v, axis, dir)
}

/** The six sticker colours (design theme.appTokens). [NONE] = not filled in yet (reading a cube). */
enum class CubeColor(val zh: String, val en: String) {
    WHITE("白", "White"),
    YELLOW("黄", "Yellow"),
    RED("红", "Red"),
    ORANGE("橙", "Orange"),
    BLUE("蓝", "Blue"),
    GREEN("绿", "Green"),
    NONE("空", "Empty");

    val code: Char
        get() = if (this == NONE) '.' else name[0]

    companion object {
        val SIX = listOf(WHITE, YELLOW, RED, ORANGE, BLUE, GREEN)

        fun ofCode(c: Char): CubeColor = values().firstOrNull { it.code == c.uppercaseChar() } ?: NONE
    }
}

/** An immutable cube state (54 colours, URFDLB). */
class Cube(private val c: Array<CubeColor>) {
    init {
        require(c.size == 54)
    }

    operator fun get(i: Int): CubeColor = c[i]

    fun colors(): List<CubeColor> = c.toList()

    fun turn(t: Turn): Cube {
        val p = t.perm
        val out = Array(54) { CubeColor.NONE }
        for (i in 0 until 54) out[p[i]] = c[i]
        return Cube(out)
    }

    fun apply(moves: List<Turn>): Cube = moves.fold(this) { a, t -> a.turn(t) }

    /** Applies a composed permutation ([Perms.compose]): facelet i moves to `where[i]`. */
    fun permute(where: IntArray): Cube {
        val out = Array(54) { CubeColor.NONE }
        for (i in 0 until 54) out[where[i]] = c[i]
        return Cube(out)
    }

    fun apply(notation: String): Cube = apply(Moves.parse(notation))

    fun with(i: Int, color: CubeColor): Cube = Cube(c.copyOf().also { it[i] = color })

    fun centerColor(face: Int): CubeColor = c[Facelets.center(face)]

    val isSolved: Boolean
        get() = (0 until 6).all { f -> (0 until 9).all { c[f * 9 + it] == c[f * 9 + 4] } }

    val filled: Boolean
        get() = c.none { it == CubeColor.NONE }

    /** "WWWWWWWWWRRR…" in URFDLB order (debug `load=`, save files). */
    fun code(): String = String(CharArray(54) { c[it].code })

    override fun equals(other: Any?): Boolean = other is Cube && other.c.contentEquals(c)

    override fun hashCode(): Int = c.contentHashCode()

    override fun toString(): String = code()

    companion object {
        /** The standard colour scheme in the tutorial hold: yellow U, red F, green R, white D, orange B, blue L (the WCA scheme turned yellow-up). */
        val SCHEME = mapOf(0 to CubeColor.YELLOW, 1 to CubeColor.GREEN, 2 to CubeColor.RED, 3 to CubeColor.WHITE, 4 to CubeColor.BLUE, 5 to CubeColor.ORANGE)

        fun solved(scheme: Map<Int, CubeColor> = SCHEME): Cube = Cube(Array(54) { scheme.getValue(it / 9) })

        fun blank(): Cube = Cube(Array(54) { CubeColor.NONE })

        fun parse(code: String): Cube? {
            if (code.length != 54) return null
            return Cube(Array(54) { CubeColor.ofCode(code[it]) })
        }
    }
}

/**
 * Notation <-> turns (design cubeSpec.notation): faces U D R L F B, whole-cube y x z, middle layers M E S; ' = counter-
 * clockwise, 2 = half turn. A half turn expands to two quarter turns.
 */
object Moves {
    private fun base(letter: Char): Turn? =
        when (letter) {
            'U' -> Turn(Axis.Y, setOf(1), -1)
            'D' -> Turn(Axis.Y, setOf(-1), 1)
            'E' -> Turn(Axis.Y, setOf(0), 1)
            'R' -> Turn(Axis.X, setOf(1), -1)
            'L' -> Turn(Axis.X, setOf(-1), 1)
            'M' -> Turn(Axis.X, setOf(0), 1)
            'F' -> Turn(Axis.Z, setOf(1), -1)
            'B' -> Turn(Axis.Z, setOf(-1), 1)
            'S' -> Turn(Axis.Z, setOf(0), -1)
            'y' -> Turn(Axis.Y, setOf(-1, 0, 1), -1)
            'x' -> Turn(Axis.X, setOf(-1, 0, 1), -1)
            'z' -> Turn(Axis.Z, setOf(-1, 0, 1), -1)
            else -> null
        }

    /** One move token ("R", "U'", "F2", "y'") as quarter turns. */
    fun token(m: String): List<Turn> {
        val t = base(m.firstOrNull() ?: return emptyList()) ?: error("bad move '$m'")
        return when (m.drop(1)) {
            "" -> listOf(t)
            "'" -> listOf(t.inverse)
            "2", "2'" -> listOf(t, t)
            else -> error("bad move '$m'")
        }
    }

    fun parse(s: String): List<Turn> = tokens(s).flatMap { token(it) }

    fun tokens(s: String): List<String> = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun inverse(m: String): String =
        when {
            m.endsWith("2") -> m
            m.endsWith("'") -> m.dropLast(1)
            else -> "$m'"
        }

    fun invert(seq: List<String>): List<String> = seq.reversed().map { inverse(it) }

    /** The quarter-turn name of a turn when it is one ("R", "U'", "y"), else null (a wide or odd turn in free play). */
    fun nameOf(t: Turn): String? {
        for (l in "UDERLMFBSyxz") {
            val b = base(l) ?: continue
            if (b.axis == t.axis && b.layers == t.layers) return if (b.dir == t.dir) "$l" else "$l'"
        }
        return null
    }

    /** True when [m] (a token) is a half turn. */
    fun isHalf(m: String): Boolean = m.endsWith("2")
}
