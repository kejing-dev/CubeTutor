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
     * A group of moves the lesson does together (user 2026-11-17: say why in the lesson itself, especially the few
     * moves around the current one: what pattern they form and why each small move is there). [moves] = the principle
     * of each move in the group (null for single-move groups), [purpose] = what the whole group does and why the solved
     * part survives it.
     */
    data class Group(val name: T, val notation: String?, val purpose: T, val moves: List<T>?)

    private fun t(zh: String, en: String) = T(zh, en)

    val groups: Map<String, Group> =
        mapOf(
            "DAISY" to Group(
                t("先搭小雏菊", "First, the daisy"), null,
                t("四条白边先都翻到顶上，围着黄中心、白色朝上。在顶上它们互不妨碍，后面再一条一条往下送。", "Bring all four white edges up around the yellow center, white facing up. Up there they don't get in each other's way; later each one goes down."),
                null,
            ),
            "DOWN" to Group(
                t("把一条白边送下去", "Send one white edge down"), null,
                t("先转顶面，让这条白边的侧面颜色对上前面的中心；再把前面转半圈，它就带着对好的颜色翻到底面，成为十字的一臂。", "Turn the top until the edge's side color meets the front center, then a front half turn flips it down with that color still matching: one arm of the cross."),
                null,
            ),
            "U" to Group(
                t("只转顶面：对准位置", "Only the top turns: line up"), null,
                t("公式都在固定的位置做（右前 / 前面），所以先转顶面把要处理的块送过去。只转顶面，底下已经拼好的两层不受影响。", "Every formula works at a fixed spot (front right / front), so the top turns first to bring the piece there. Only the top moves; the finished layers below stay put."),
                null,
            ),
            "Y" to Group(
                t("整只转：换一面朝你", "Turn the whole cube: another side faces you"), null,
                t("整只魔方转一下，魔方本身没变，只是让要处理的槽转到右前——公式都在右前做。", "The whole cube turns; nothing on it changes. It just brings the slot you need to front right, where the formulas work."),
                null,
            ),
            "SEXY" to Group(
                t("右手小公式", "The right-hand move"), "R U R' U'",
                t("抬起槽 → 新角对到槽口 → 放下 → 顶面回正。右面上去又下来、顶面左去又右回，所以白十字原样，变的只有右前下那个角。一遍不一定白色朝下：每做一遍它会拧一下，重复 1–5 遍就正。", "Lift the slot, bring the new corner over it, put it down, turn the top back. Right goes up and down, top goes left and back, so the cross stays; only the front-right bottom corner changes. Each round twists it once; 1–5 rounds and white faces down."),
                listOf(
                    t("右面往上：底层右前的角槽抬上来；顶上右前的那个新角被推到右后。", "Right up: the bottom front-right slot comes up; the new corner on top is pushed to the back right."),
                    t("顶面往左：新角从右后转回右前，正好在槽口上方；抬上来的旧角被让到左前。", "Top left: the new corner swings back to front right, right over the slot; the old corner moves aside to front left."),
                    t("右面往下：右面放回，新角跟着落进槽里；十字那条白边也回到原位。", "Right down: the right side goes back and takes the new corner into the slot; the cross edge is home again."),
                    t("顶面往右：顶面转回去。整组下来只换了槽里那个角。", "Top right: the top turns back. The whole round only changed the corner in the slot."),
                ),
            ),
            "RB" to Group(
                t("右边腰带公式", "Right belt formula"), "U R U' R' · U' F' U F",
                t("8 下分两半。前 4 下：把右前的白角抬出来，在顶上和要放的那条棱配成一对；后 4 下：用前面把这一对一起放进中层右前。白面最后原样，中层多了一条对的棱。", "Two halves. First four: lift the white corner out of the front-right slot and pair it with the edge on top. Last four: use the front side to drop the pair in together. The white face ends as it was, plus one middle edge done."),
                listOf(
                    t("顶面往左：棱先让开，免得被右面带走。", "Top left: the edge steps aside so the right turn won't take it."),
                    t("右面往上：右前的槽（中层棱位 + 白角）抬到顶上。", "Right up: the front-right slot (middle edge spot + white corner) comes up."),
                    t("顶面往右：棱回到前面；抬上来的白角被转到右后。", "Top right: the edge is back at the front; the white corner moves to the back right."),
                    t("右面往下：右面放回；白角留在顶上右前，正好挨着前面那条棱——配成一对。", "Right down: the right side goes back; the white corner stays on top at front right, right next to the edge: a pair."),
                    t("顶面往右：这一对挪到右边，把前面让出来。", "Top right: the pair moves to the right and frees the front."),
                    t("前面逆时针：前面翻上来，空着的槽升到顶上前面。", "Front counter-clockwise: the front flips up and the empty slot rises to the top front."),
                    t("顶面往左：配好的一对转回前面，正好在空槽上方。", "Top left: the pair comes back to the front, right above the empty slot."),
                    t("前面顺时针：这一对一起落进中层右前，白角回到底层。", "Front clockwise: the pair drops into the middle together; the white corner is back at the bottom."),
                ),
            ),
            "LB" to Group(
                t("左边腰带公式（右边的照镜子）", "Left belt formula (the right one in a mirror)"), "U' L' U L · U F U' F'",
                t("和右边腰带一样分两半，只是左右对调：前 4 下把左前的白角抬出来和棱配对，后 4 下用前面把这一对一起放进中层左前。", "Same two halves as the right belt, mirrored: lift the front-left white corner and pair it with the edge, then use the front side to drop the pair into the middle on the left."),
                listOf(
                    t("顶面往右：棱先让开，免得被左面带走。", "Top right: the edge steps aside so the left turn won't take it."),
                    t("左面往上：左前的槽（中层棱位 + 白角）抬到顶上。", "Left up: the front-left slot (middle edge spot + white corner) comes up."),
                    t("顶面往左：棱回到前面；白角被转到左后。", "Top left: the edge is back at the front; the white corner moves to the back left."),
                    t("左面往下：左面放回；白角留在顶上左前，挨着前面那条棱——配成一对。", "Left down: the left side goes back; the white corner stays on top at front left, next to the edge: a pair."),
                    t("顶面往左：这一对挪到左边，把前面让出来。", "Top left: the pair moves to the left and frees the front."),
                    t("前面顺时针：前面翻上来，空着的槽升到顶上前面。", "Front clockwise: the front flips up and the empty slot rises to the top front."),
                    t("顶面往右：配好的一对转回前面，在空槽上方。", "Top right: the pair comes back to the front, above the empty slot."),
                    t("前面逆时针：这一对一起落进中层左前，白角回到底层。", "Front counter-clockwise: the pair drops in together; the white corner is back at the bottom."),
                ),
            ),
            "YC" to Group(
                t("黄十字公式：前面夹着右手小公式", "Yellow cross: the right-hand move inside a front turn"), "F · R U R' U' · F'",
                t("F 先把前面这一层\u201c借\u201d过来，中间做右手小公式，F' 再还回去——所以底下两层原样，只有顶面变。顶面黄色按 点 → L 形 → 一字 → 十字 一遍进一步：L 形摆在左后（黄在后面和左边），一字横着放。", "F borrows the front layer, the right-hand move works on it, F' gives it back, so the two bottom layers survive and only the top changes. The yellow goes dot → L → line → cross, one step per round: the L at back left, the line left to right."),
                listOf(
                    t("前面顺时针：先借——顶层前面那条边被带进右边那一列。", "Front clockwise: borrow. The top front edge moves into the right column."),
                    t("右面往上（右手小公式第 1 下）：右边那列抬上来。", "Right up (right-hand move, 1): the right column comes up."),
                    t("顶面往左（第 2 下）：顶层的块换个位置。", "Top left (2): the top pieces shift."),
                    t("右面往下（第 3 下）：右边那列放回。", "Right down (3): the right column goes back."),
                    t("顶面往右（第 4 下）：顶面转回来。", "Top right (4): the top turns back."),
                    t("前面逆时针：还回去，底下两层复原，顶面多了黄色。", "Front counter-clockwise: give it back. The bottom two layers are whole again; more yellow on top."),
                ),
            ),
            "SWAP" to Group(
                t("换边公式（小鱼公式 + 转一下顶面）", "Edge swap (the fish formula + one top turn)"), "R U R' U R U2 R' · U",
                t("R…R' 把右边一列抬起又放回，中间只转顶面，让顶层的块换座位；所以做完底下两层全回原样，顶层前面和左边两条棱换了位置。先转顶面把已经对的两条放到后面和右边，它们就不会被换走。顶层的角会被挪动或拧，没关系，后两课专门摆角。", "R … R' lifts the right column and puts it back while only the top turns, so the top pieces trade seats and the bottom comes back. Front and left top edges swap, so put the two good ones at the back and right first. Corners may move; stages 6–7 fix them."),
                listOf(
                    t("右面往上：右边一列抬到顶上，跟着顶面一起转。", "Right up: the right column comes up to ride with the top."),
                    t("顶面往左：只转顶层，顶层的块挪座位。", "Top left: only the top turns; the top pieces shift seats."),
                    t("右面往下：放回去，底下又是完整的。", "Right down: back down; the bottom is whole again."),
                    t("顶面往左：顶层再挪一格。", "Top left: the top shifts once more."),
                    t("右面往上：再抬一次。", "Right up: lift again."),
                    t("顶面转半圈：顶层挪两格。", "Top half turn: the top shifts two seats."),
                    t("右面往下：放回去——底下两层回原样。", "Right down: back down. The bottom two layers are back."),
                    t("顶面往左：最后对齐，前面和左边两条棱换好了。", "Top left: line up. The front and left edges have swapped."),
                ),
            ),
            "CYCLE" to Group(
                t("三角轮换公式（A B A\u207b\u00b9 B\u207b\u00b9）", "Three-corner cycle (A B A\u207b\u00b9 B\u207b\u00b9)"), "U R U' · L' · U R' U' · L",
                t("这是\u201c交换子\u201d：做 A（U R U'）、做 B（L'）、撤销 A（U R' U'）、撤销 B（L）。A 只动右边、B 只动左边，只在顶上几个角相遇，所以其余全还原，三个角各挪一格。右前的角不动——先把在家的角放右前。", "A commutator: do A (U R U'), do B (L'), undo A, undo B. A only touches the right, B only the left; they overlap at a few top corners, so all else returns and three corners move one spot. The front-right corner stays: put a home corner there first."),
                listOf(
                    t("A 段：顶面往左。", "A: top left."),
                    t("A 段：右面往上——右边一个顶角绕下去。", "A: right up. A right top corner swings down."),
                    t("A 段：顶面往右。A 做完了。", "A: top right. A is done."),
                    t("B：左面往上——左边那一列换了一个角上来。", "B: left up. The left column brings another corner up."),
                    t("撤销 A：顶面往左。", "Undo A: top left."),
                    t("撤销 A：右面往下。", "Undo A: right down."),
                    t("撤销 A：顶面往右。", "Undo A: top right."),
                    t("撤销 B：左面往下——其余全回原样，三个角各挪了一格。", "Undo B: left down. Everything else is back; three corners each moved one spot."),
                ),
            ),
            "TWIST" to Group(
                t("拧角公式", "Corner twist"), "R' D' R D",
                t("只管右前上这个角：每做两遍它原地拧 1/3 圈，做到黄色朝上（2 或 4 遍）。底下会乱，正常：四个角都拧正时总遍数是 6 的倍数，而这串做 6 遍等于没做，所以底下自己回来。中途只转顶面，别整只转、别换手。", "Only the front-right top corner counts: every 2 rounds twist it 1/3; repeat until yellow is up. The messy bottom is fine: with all four corners right you've done a multiple of 6 rounds, and 6 rounds change nothing. Between corners turn only the top."),
                listOf(
                    t("右面往下：把右前上这个角带到底下。", "Right down: the front-right top corner goes down."),
                    t("底面往左：底层转一格，换个方向。", "Bottom left: the bottom turns one spot."),
                    t("右面往上：右边一列回到顶上。", "Right up: the right column comes back up."),
                    t("底面往右：底面转回来。底下暂时乱是正常的。", "Bottom right: the bottom turns back. A messy bottom for now is normal."),
                ),
            ),
        )

    /** The "why" lines of a step: group name, its notation, this move's principle, the whole group's purpose. */
    data class WhyNow(val name: String, val notation: String?, val pos: Int, val len: Int, val rep: String?, val move: String, val purpose: String)

    fun whyNow(s: Solver.Step, zh: Boolean): WhyNow? {
        val g = groups[s.group] ?: return null
        val moveLine =
            when {
                s.reason.endsWith(".out") -> if (zh) "这一遍先把卡住的块换出来，下一组再放好。" else "This round takes the stuck piece out first; the next one puts it in."
                g.moves != null -> g.moves.getOrNull(s.pos)?.of(zh) ?: reason(s.reason).of(zh)
                else -> reason(s.reason).of(zh).ifEmpty { caption(s.move).of(zh) }
            }
        val rep =
            when {
                s.reps > 1 -> if (zh) "第 ${s.rep + 1} / ${s.reps} 遍" else "round ${s.rep + 1} of ${s.reps}"
                s.reps == 0 -> if (zh) "第 ${s.rep + 1} 遍" else "round ${s.rep + 1}"
                else -> null
            }
        return WhyNow(g.name.of(zh), g.notation, s.pos, s.len, rep, moveLine, g.purpose.of(zh))
    }

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
