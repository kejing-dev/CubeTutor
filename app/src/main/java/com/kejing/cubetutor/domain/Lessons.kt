package com.kejing.cubetutor.domain

/**
 * Lesson copy, copied from the design package (content.lessons, cubeSpec.moveCaptions, cubeSpec.reasonVocab,
 * content.demoSetups): stage names, tags, goals, whys, the plain-words caption of every move and the short reasons.
 * Two languages side by side ([T]); the UI picks one with the app's language.
 */
data class T(val zh: String, val en: String) {
    fun of(zhLang: Boolean) = if (zhLang) zh else en
}

data class LessonInfo(val id: Int, val name: T, val tag: T, val goal: T, val why: T, val demo: List<Pair<String, T>>, val doneTitle: T)

object Lessons {
    val all: List<LessonInfo> =
        listOf(
            LessonInfo(
                1, T("先搭白十字", "White cross first"), T("四条白边找到家", "Four white edges go home"),
                T("底面拼出白十字，四条白边的侧面颜色和旁边中心对上。", "Make a white cross on the bottom, each edge matching its side center."),
                T("十字是地基：四条白边放好，四个侧面的中心就有了对应，后面每一块都照着它找位置。", "The cross is the foundation. Once the four white edges sit right, every other piece knows where home is."),
                listOf("U" to T("顶面往左：把白边带到同色中心正上方", "Top left: bring the white edge over its matching center"), "F2" to T("前面转半圈：白边从顶面翻到底面，侧色还对着中心", "Front half turn: the edge flips down, side color still on its center")),
                T("白十字搭好了", "White cross done"),
            ),
            LessonInfo(
                2, T("把白面补满", "Fill the white face"), T("角块抬出来，再放回去", "Lift the corner out, put it back"),
                T("四个白角放进底层，底面全白、底层一圈侧面颜色对齐。", "Put the four white corners in. Whole bottom white, first layer sides lined up."),
                T("右面往上、顶面往左、右面往下、顶面往右这四下，把角槽抬到顶层换进新角，再原样放回去，所以拼好的白十字不会动。", "Right up, top left, right down, top right lifts the slot, swaps a corner in and puts everything back, so your cross stays put."),
                listOf(
                    "R" to T("右面往上：角槽抬到顶层，让出位置", "Right up: the slot comes up and makes room"),
                    "U" to T("顶面往左：新角挪进来", "Top left: the new corner moves in"),
                    "R'" to T("右面往下：放回去，十字原样", "Right down: back it goes, cross untouched"),
                    "U'" to T("顶面往右：顶面转回来，准备下一轮", "Top right: top comes back, ready for another round"),
                ),
                T("白面补满了", "White face done"),
            ),
            LessonInfo(
                3, T("系上腰带", "Put the belt on"), T("中间一圈，左右镜像", "The middle ring, left or right"),
                T("中层四条棱放好，下面两层全对。", "Place the four middle edges. Bottom two layers done."),
                T("先让棱躲到顶层，转一下顶面腾位置，再把它和角一起带回中层；往左放和往右放是同一个公式照镜子。", "The edge hides in the top layer, the top turns to make room, then it comes back down with its corner. Left and right are mirror images."),
                listOf(
                    "U" to T("顶面往左：棱先躲开", "Top left: the edge steps aside"), "R" to T("右面往上", "Right up"), "U'" to T("顶面往右", "Top right"),
                    "R'" to T("右面往下：角带回来", "Right down: the corner comes back"), "U'" to T("顶面往右", "Top right"), "F'" to T("前面逆时针", "Front counter-clockwise"),
                    "U" to T("顶面往左", "Top left"), "F" to T("前面顺时针：棱和角一起归位", "Front clockwise: edge and corner home together"),
                ),
                T("腰带系好了", "Belt is on"),
            ),
            LessonInfo(
                4, T("顶上画个黄十字", "Yellow cross on top"), T("一个公式，最多三遍", "One formula, three times at most"),
                T("顶面拼出黄十字（角先不管）。", "Get a yellow cross on top. Ignore the corners for now."),
                T("中间那四下只动顶层和右前那一块，前后包一层「前面顺、前面逆」，底下两层就原样回来，变的只有顶面的黄色。", "The middle four moves only touch the top. Wrapping them in front clockwise and back again keeps the bottom two layers safe."),
                listOf(
                    "F" to T("前面顺时针：把顶层一条边带下来", "Front clockwise: one top edge comes down"), "R" to T("右面往上", "Right up"), "U" to T("顶面往左", "Top left"),
                    "R'" to T("右面往下", "Right down"), "U'" to T("顶面往右", "Top right"), "F'" to T("前面逆时针：带回去，顶面多了黄色", "Front counter-clockwise: back up, more yellow on top"),
                ),
                T("黄十字有了", "Yellow cross done"),
            ),
            LessonInfo(
                5, T("黄十字对齐四边", "Line up the cross"), T("换两条，底下不动", "Swap two, bottom stays"),
                T("黄十字四条边的侧面颜色都对上中心。", "Each yellow edge's side color matches its center."),
                T("这串只换顶层前面和左边两条棱，做完底下两层自己归位；所以先转顶面，让已经对的两条站在后面和右边。", "This swaps only the front and left top edges and the rest comes back, so first spin the top until the two good edges sit at the back and right."),
                listOf(
                    "R" to T("右面往上", "Right up"), "U" to T("顶面往左", "Top left"), "R'" to T("右面往下", "Right down"), "U" to T("顶面往左", "Top left"),
                    "R" to T("右面往上", "Right up"), "U2" to T("顶面转半圈", "Top half turn"), "R'" to T("右面往下", "Right down"),
                    "U" to T("顶面往左：前和左两条换了位置", "Top left: front and left edges swapped"),
                ),
                T("四边对齐了", "Cross lined up"),
            ),
            LessonInfo(
                6, T("四个角各回各家", "Corners go home"), T("三个角轮一圈", "Three corners take a lap"),
                T("四个黄角都到自己的位置（颜色朝向先不管）。", "All four top corners in the right spot. Twist doesn't matter yet."),
                T("这八下让顶层三个角转一圈，第四个角不动；先找一个已经在家的角放到右前，它就是那个不动的。", "These eight moves cycle three corners and leave one alone. Put a corner that's already home at front right, and it stays."),
                listOf(
                    "U" to T("顶面往左", "Top left"), "R" to T("右面往上", "Right up"), "U'" to T("顶面往右", "Top right"), "L'" to T("左面往上", "Left up"),
                    "U" to T("顶面往左", "Top left"), "R'" to T("右面往下", "Right down"), "U'" to T("顶面往右", "Top right"),
                    "L" to T("左面往下：三个角各走了一格", "Left down: three corners each moved one spot"),
                ),
                T("四个角都到家了", "Corners are home"),
            ),
            LessonInfo(
                7, T("最后把角拧正", "Twist the corners"), T("中途乱了别慌", "It looks messy midway. Keep going"),
                T("四个黄角都黄色朝上，整只复原。", "All yellow faces up. Solved."),
                T("右面往下、底面往左、右面往上、底面往右只拧右前上那个角；做的时候下面两层会乱，四个角都拧正以后，底下才一起回来，中途别换手、别整只转。", "Right down, bottom left, right up, bottom right twists only the front-right top corner. The bottom gets messy and only comes back once all four corners are done, so don't regrip or turn the whole cube."),
                // design demo: (R' D' R D) × 2 → U → (R' D' R D) × 4 → U' (26 moves)
                List(2) { listOf("R'", "D'", "R", "D") }.flatten().mapIndexed { i, m -> m to (if (i == 7) T("这个角拧正了，底下还乱着，别慌", "This corner is right. Bottom's still messy, don't worry") else T("拧这个角", "Twist this corner")) } +
                    listOf("U" to T("只转顶面，把下一个角送过来", "Only the top turns, next corner comes over")) +
                    List(4) { listOf("R'", "D'", "R", "D") }.flatten().mapIndexed { i, m -> m to (if (i == 15) T("第二个角也拧正了", "Second corner done too") else T("拧这个角", "Twist this corner")) } +
                    listOf("U'" to T("顶面转回来：四个角都正，底下一起回来了", "Top back: all corners right, the bottom comes back too")),
                T("复原了", "Solved"),
            ),
        )

    fun info(k: Int): LessonInfo = all[(k - 1).coerceIn(0, 6)]

    /** Plain-words captions (cubeSpec.moveCaptions); B / middle layers for free play captions. */
    val captions: Map<String, T> =
        mapOf(
            "U" to T("顶面往左", "Top layer left"), "U'" to T("顶面往右", "Top layer right"), "U2" to T("顶面转半圈", "Top layer half turn"),
            "D" to T("底面往右", "Bottom layer right"), "D'" to T("底面往左", "Bottom layer left"), "D2" to T("底面转半圈", "Bottom layer half turn"),
            "R" to T("右面往上", "Right layer up"), "R'" to T("右面往下", "Right layer down"), "R2" to T("右面转半圈", "Right layer half turn"),
            "L" to T("左面往下", "Left layer down"), "L'" to T("左面往上", "Left layer up"), "L2" to T("左面转半圈", "Left layer half turn"),
            "F" to T("前面顺时针", "Front layer clockwise"), "F'" to T("前面逆时针", "Front layer counter-clockwise"), "F2" to T("前面转半圈", "Front layer half turn"),
            "B" to T("后面逆时针", "Back layer counter-clockwise"), "B'" to T("后面顺时针", "Back layer clockwise"), "B2" to T("后面转半圈", "Back layer half turn"),
            "y" to T("整个魔方往左转：右边那面转到你面前", "Turn the whole cube: the right side comes to the front"),
            "y'" to T("整个魔方往右转：左边那面转到你面前", "Turn the whole cube the other way: the left side comes to the front"),
            "y2" to T("整个魔方转半圈：后面转到你面前", "Turn the whole cube around: the back comes to the front"),
        )

    fun caption(move: String): T = captions[move] ?: T(move, move)

    /** Short reasons (cubeSpec.reasonVocab, keys from [Solver]). */
    val reasons: Map<String, T> =
        mapOf(
            "L1.room" to T("先腾个空位", "Make room first"),
            "L1.lift" to T("把白边抬到顶上", "Lift the white edge to the top"),
            "L1.ring" to T("绕着黄中心排一圈", "Line them up around the yellow center"),
            "L1.align" to T("转顶面对齐中心", "Turn the top to match the center"),
            "L1.down" to T("送到底面", "Send it to the bottom"),
            "L2.above" to T("把白角挪到要去的槽正上方", "Bring the white corner above its slot"),
            "L2.lift" to T("角槽抬起来", "The slot comes up"),
            "L2.in" to T("新角挪进去", "The new corner moves in"),
            "L2.back" to T("放回去，十字不动", "Back down, cross stays"),
            "L2.top" to T("顶面转回来，准备下一轮", "Top comes back, ready for another round"),
            "L2.out" to T("卡住的角先拿出来", "Get the stuck corner out first"),
            "L3.hide" to T("棱先躲开", "The edge steps aside"),
            "L3.room" to T("腾出位置", "Make room"),
            "L3.back" to T("角带回来", "The corner comes back"),
            "L3.home" to T("棱跟着回家", "The edge follows it home"),
            "L3.align" to T("转顶面让颜色对上中心", "Turn the top so the color meets its center"),
            "L3.out" to T("卡反的棱先换出来", "Swap the flipped edge out first"),
            "L4.down" to T("把顶层一条边带下来", "One top edge comes down"),
            "L4.swap" to T("顶层换一换", "Shuffle the top"),
            "L4.up" to T("带回去，顶上多了黄色", "Back up, more yellow on top"),
            "L4.shape" to T("把 L 形 / 一字摆好", "Set the L or the line in place"),
            "L5.place" to T("对的两条放到后面和右边", "Good edges to the back and right"),
            "L5.swap" to T("换前面和左边两条", "Swap the front and left edges"),
            "L5.last" to T("最后转顶面对齐", "Last, turn the top to line up"),
            "L6.place" to T("在家的角放右前", "Home corner to front right"),
            "L6.cycle" to T("三个角轮一格", "Three corners move one spot"),
            "L7.twist" to T("拧这个角", "Twist this corner"),
            "L7.next" to T("只转顶面，换下一个角", "Only the top turns, next corner"),
            "L7.last" to T("最后转顶面对齐", "Last, turn the top to line up"),
            "y.slot" to T("把要放的槽转到右前", "Bring the slot to front right"),
            "y.next" to T("换个面朝你，方便下一下", "Another side faces you, easier next move"),
        )

    fun reason(key: String): T = reasons[key] ?: T("", "")

    /**
     * The fixed teaching position of a stage (content.demoSetups): solved cube + the stage's demo formula done backwards,
     * so playing the demo forwards ends solved. Pure: same picture every time.
     */
    fun demoStart(k: Int): Cube {
        val demo = info(k).demo.map { it.first }
        return Cube.solved().apply(Moves.invert(demo).joinToString(" "))
    }

    /** Coach lines for the first start (content.coachFirstTime). */
    val coach: List<T> =
        listOf(
            T("手里有魔方就读进来，没有就直接玩。", "Got a cube? Read it in. No cube? Just play."),
            T("点魔方旁边亮着的把手，再推一下摇杆就转。", "Tap the glowing handle next to the cube, then push the stick."),
            T("按住魔方下面的托盘，能把它挪到顺手的地方。", "Hold the tray under the cube to move it somewhere comfy."),
            T("先拧你手里那只，拧好了点「转好了」。", "Turn your cube first, then tap Done."),
        )

    /** How to hold the cube for face n of reading (cubeSpec.readOrder), 1..6. */
    val readHow: List<T> =
        listOf(
            T("黄中心朝上，任意一面对着你", "Yellow center up, any side facing you"),
            T("整个魔方往左转一下，顶面还朝上", "Turn the whole cube left once, top still up"),
            T("再往左转一下", "Turn left once more"),
            T("再往左转一下", "And once more"),
            T("转回第 1 面对着你，再把顶面翻向你", "Back to side 1 facing you, then tip the top toward you"),
            T("接着同方向再翻半圈，白中心对着你", "Keep tipping the same way, white center facing you"),
        )

    /**
     * How to hold face [face] (URFDLB) so the picture reads row by row (centres fix the faces, user addendum): sides with
     * yellow on top; yellow with the red side down; white with the red side up.
     */
    fun holdFor(face: Int): T =
        when (face) {
            0 -> T("红中心那面朝下", "Red-center side down")
            3 -> T("红中心那面朝上", "Red-center side up")
            else -> T("黄中心朝上", "Yellow center on top")
        }

    /** Reading order: face n (1..6) is which URFDLB face (F R B L U D) and how the virtual cube turns to show it. */
    val readFaces = intArrayOf(2, 1, 5, 4, 0, 3)
}
