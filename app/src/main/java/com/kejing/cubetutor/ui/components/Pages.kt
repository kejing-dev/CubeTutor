package com.kejing.cubetutor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import com.kejing.cubetutor.BuildConfig
import com.kejing.cubetutor.R
import com.kejing.cubetutor.domain.CubeColor
import com.kejing.cubetutor.domain.Handle
import com.kejing.cubetutor.domain.Legality
import com.kejing.cubetutor.domain.Lessons
import com.kejing.cubetutor.domain.Push
import com.kejing.cubetutor.domain.Row as CubeRow
import com.kejing.cubetutor.domain.Selection
import com.kejing.cubetutor.domain.T
import com.kejing.cubetutor.ui.CtController
import com.kejing.cubetutor.ui.CtUiState
import com.kejing.cubetutor.ui.CamIssue
import com.kejing.cubetutor.ui.ReadMode
import com.kejing.cubetutor.ui.ReadState
import com.kejing.cubetutor.domain.Facelets
import com.kejing.cubetutor.domain.scan.Guard
import com.kejing.cubetutor.ui.HintKind
import com.kejing.cubetutor.ui.Lang
import com.kejing.cubetutor.ui.LessonMode
import com.kejing.cubetutor.ui.SettingsAsk
import com.kejing.cubetutor.ui.Ui
import com.kejing.cubetutor.ui.debug.probe
import com.kejing.cubetutor.ui.theme.CtColors
import com.kejing.cubetutor.ui.theme.CtType
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// =================================================================================================== shared pieces

/** PRIMARY gold, DANGER light red, NORMAL system fill (on the glass), LIGHT pale key with dark ink (inside a dark card). */
enum class KeyKind { PRIMARY, DANGER, NORMAL, LIGHT }

/** Is the app showing Chinese right now (lesson copy comes from the bilingual table [T]). */
@Composable
fun zh(): Boolean = LocalCtResources.current.configuration.locales[0].language == "zh"

@Composable
fun T.txt(): String = of(zh())

/** A text key, ≥ 48 dp, icon + words (B19: no bare icons). Disabled keys keep readable text and say why elsewhere. */
@Composable
fun Key(text: String, name: String, icon: Int? = null, kind: KeyKind = KeyKind.NORMAL, height: Dp = 64.dp, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val container = when (kind) {
        KeyKind.PRIMARY -> CtColors.Interaction
        KeyKind.DANGER -> CtColors.DangerFill
        KeyKind.NORMAL -> PicoTheme.colorScheme.fillTertiary
        KeyKind.LIGHT -> CtColors.TagInk
    }
    val ink = when (kind) {
        KeyKind.NORMAL -> PicoTheme.colorScheme.labelPrimary
        KeyKind.DANGER -> CtColors.OnError
        else -> CtColors.OnFill
    }
    Button(
        onClick = onClick,
        modifier = modifier.probe(name).height(height),
        enabled = enabled,
        size = ButtonDefaults.Regular.copy(height = height),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = ink),
    ) {
        if (icon != null) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp), tint = ink)
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, maxLines = 1, color = ink, style = CtType.button, onTextLayout = fits("key/$name"))
    }
}

/** Page title + the mute key in the top-right corner (D15: one look, every page). */
@Composable
fun TitleRow(title: String, state: CtUiState, onEvent: (Ui) -> Unit, extra: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = CtType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, modifier = Modifier.weight(1f).probe("title"), onTextLayout = fits("title/$title"))
        extra?.invoke(this)
        Spacer(Modifier.width(8.dp))
        val muted = state.prefs.muted
        Key(rs(if (muted) R.string.sound_off else R.string.sound_on), "mute", if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up, KeyKind.NORMAL, 64.dp) {
            onEvent(Ui.SetPrefs("mute") { it.copy(muted = !it.muted) })
        }
    }
    if (state.away) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(CtColors.WarnBg).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = rs(R.string.away), style = CtType.body, color = CtColors.WarnInk, maxLines = 2, modifier = Modifier.weight(1f), onTextLayout = fits("away/line"))
            Key(rs(R.string.recenter), "away_recenter", R.drawable.ic_my_location, KeyKind.NORMAL, 64.dp) { onEvent(Ui.Recenter) }
        }
    }
}

@Composable
fun Line(text: String, name: String, style: androidx.compose.ui.text.TextStyle = CtType.body, color: Color = PicoTheme.colorScheme.labelPrimary, lines: Int = 2, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(text = text, style = style, color = color, maxLines = lines, modifier = modifier.probe(name), textAlign = align ?: TextAlign.Unspecified, onTextLayout = fits("line/$name"))
}

/** One line of gold on a dark slate pill (the lesson cue; gold straight on the light glass is only ~1.7:1, D13). */
@Composable
fun Pill(text: String, name: String, style: androidx.compose.ui.text.TextStyle = CtType.titleSmall) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(CtColors.TagBg).padding(horizontal = 14.dp, vertical = 6.dp).probe(name)) {
        Text(text = text, style = style, color = CtColors.Interaction, maxLines = 2, onTextLayout = fits("pill/$name"))
    }
}

/** A dark slate card (13:1 for TagInk). */
@Composable
fun Card(modifier: Modifier = Modifier, name: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(CtColors.TagBg).padding(horizontal = 24.dp, vertical = 22.dp).probe(name), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), content = content)
}

@Composable
private fun segColors() =
    SegmentControlDefaults.colors(
        itemBackgroundColor = Color.Transparent,
        itemContentColor = PicoTheme.colorScheme.labelPrimary,
        selectedItemBackgroundColor = CtColors.Interaction,
        selectedItemContentColor = CtColors.OnFill,
    )

/** Options inside one row (not page switching: pages use the system TabBar, D8), 48 dp. */
@Composable
fun <V> Options(label: String, options: List<Pair<V, String>>, selected: V, name: String, itemWidth: Dp, onPick: (V) -> Unit) {
    Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = CtType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, modifier = Modifier.weight(1f), onTextLayout = fits("opt/$name"))
        SegmentControl(modifier = Modifier.width(itemWidth * options.size + 8.dp).height(56.dp), backgroundColor = PicoTheme.colorScheme.fillTertiary) {
            options.forEach { (v, text) ->
                val sel = v == selected
                SegmentItem(
                    selected = sel,
                    onClick = { onPick(v) },
                    modifier = Modifier.width(itemWidth).height(48.dp).probe("${name}_$text"),
                    textStyle = CtType.label,
                    colors = segColors(),
                    title = { Text(text = text, maxLines = 1, color = if (sel) CtColors.OnFill else PicoTheme.colorScheme.labelPrimary, style = CtType.label, onTextLayout = fits("seg/${name}_$text")) },
                )
            }
        }
    }
}

fun colorOf(c: CubeColor): Color =
    when (c) {
        CubeColor.WHITE -> CtColors.StickerWhite
        CubeColor.YELLOW -> CtColors.StickerYellow
        CubeColor.RED -> CtColors.StickerRed
        CubeColor.ORANGE -> CtColors.StickerOrange
        CubeColor.BLUE -> CtColors.StickerBlue
        CubeColor.GREEN -> CtColors.StickerGreen
        CubeColor.NONE -> CtColors.Unfilled
    }

@Composable
fun colorName(c: CubeColor): String = if (zh()) c.zh else c.en

/** The six colour keys (64 dp, design panels P1: 6 色调色板 64 dp), each with its name under it. */
@Composable
fun Palette(selected: CubeColor?, name: String, onPick: (CubeColor) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (c in CubeColor.SIX) {
            val sel = c == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                Button(
                    onClick = { onPick(c) },
                    modifier = Modifier.size(64.dp).probe("${name}_${c.name.lowercase()}").then(if (sel) Modifier.border(4.dp, CtColors.Interaction, CircleShape) else Modifier),
                    size = ButtonDefaults.Regular.copy(height = 64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colorOf(c), contentColor = CtColors.OnFill),
                ) {
                    if (sel) Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(26.dp), tint = if (c == CubeColor.BLUE || c == CubeColor.GREEN || c == CubeColor.RED) Color.White else CtColors.OnFill)
                }
                Text(text = colorName(c), style = CtType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("$name/name_${c.name}"))
            }
        }
    }
}

/** Handle names on the window, same words as on the 3D handles. */
@Composable
fun handleName(h: Handle): String {
    val z = zh()
    if (h.slot == Handle.ALL) return if (z) "整只" else "Whole"
    return when (h.row) {
        CubeRow.ROW -> when (h.slot) { 1 -> if (z) "上" else "Top"; 0 -> if (z) "中" else "Mid"; else -> if (z) "下" else "Bottom" }
        CubeRow.COL -> when (h.slot) { -1 -> if (z) "左" else "Left"; 0 -> if (z) "中" else "Mid"; else -> if (z) "右" else "Right" }
        CubeRow.DEPTH -> when (h.slot) { 1 -> if (z) "前" else "Front"; 0 -> if (z) "中" else "Mid"; else -> if (z) "后" else "Back" }
    }
}

/** 「点「右」，往上推」 — which handle, which way (design input.lesson). */
@Composable
fun howTo(h: Handle, p: Push): String {
    val dir = when (h.row) {
        CubeRow.ROW -> rs(if (p == Push.LEFT) R.string.push_left else R.string.push_right)
        CubeRow.COL -> rs(if (p == Push.UP) R.string.push_up else R.string.push_down)
        CubeRow.DEPTH -> rs(if (p == Push.RIGHT || p == Push.DOWN) R.string.push_cw else R.string.push_ccw)
    }
    return rs(R.string.how_to, handleName(h), dir)
}

@Composable
fun selectionText(sel: Selection?): String {
    if (sel == null) return rs(R.string.pick_none)
    val names = sel.layers.sortedDescending().map { handleName(Handle(sel.row, it)) }
    if (sel.whole) return rs(if (sel.row == CubeRow.ROW) R.string.pick_whole_row else if (sel.row == CubeRow.COL) R.string.pick_whole_col else R.string.pick_whole_depth)
    val kind = rs(when (sel.row) { CubeRow.ROW -> R.string.kind_row; CubeRow.COL -> R.string.kind_col; CubeRow.DEPTH -> R.string.kind_depth })
    return names.joinToString(" + ") { "「$it」" } + " " + kind
}

@Composable
fun hintText(state: CtUiState): String? =
    when (state.hint?.kind) {
        HintKind.SWITCHED -> rs(R.string.hint_switched, rs(when (state.selection?.row) { CubeRow.ROW -> R.string.kind_row; CubeRow.COL -> R.string.kind_col; else -> R.string.kind_depth }))
        HintKind.NO_PICK -> rs(R.string.hint_no_pick)
        HintKind.ROW_LR -> rs(R.string.hint_row_lr)
        HintKind.COL_UD -> rs(R.string.hint_col_ud)
        HintKind.BUSY -> rs(R.string.hint_busy)
        HintKind.HALF -> rs(R.string.hint_half)
        null -> null
    }

/** S19 「换一只魔方？」 in place. */
@Composable
fun ColumnScope.ReplaceConfirm(onEvent: (Ui) -> Unit) {
    Card(Modifier.fillMaxWidth(), "replace") {
        Line(rs(R.string.replace_q), "replace_q", CtType.big, CtColors.TagInk)
        Line(rs(R.string.replace_body), "replace_body", CtType.title, CtColors.TagInk)
    }
    Key(rs(R.string.replace_no), "replace_no", R.drawable.ic_close, KeyKind.NORMAL, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.ReplaceNo) }
    Key(rs(R.string.replace_yes), "replace_yes", R.drawable.ic_check, KeyKind.DANGER, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.ReplaceYes) }
}

/** Key height on short pages (confirmations, results): big keys fill the page instead of empty glass (D44, B19). */
val SHORT_KEY = 80.dp

/** Short pages: content centred vertically (D44 空白 ≤ 25 %). */
@Composable
fun Centered(content: @Composable ColumnScope.() -> Unit) {
    // q1-2: cards wrap their content (no stretched dark card with empty bands inside); the items share the page evenly,
    // and short pages have their keys outside the card, stacked full width, so no band is > 20 % (ui-audit §7)
    Column(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.SpaceEvenly, content = content)
}

// =================================================================================================== 开始 Home (S01 / S02–S08 / S19)

@Composable
fun HomePage(state: CtUiState, onEvent: (Ui) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        when {
            state.replaceAsk != null -> {
                TitleRow(rs(R.string.app_name), state, onEvent)
                Centered { ReplaceConfirm(onEvent) }
            }
            state.read != null -> ReadPanel(state, onEvent)
            state.ready -> ReadyPanel(state, onEvent)
            else -> HomeMain(state, onEvent)
        }
    }
}

@Composable
private fun HomeMain(state: CtUiState, onEvent: (Ui) -> Unit) {
    TitleRow(rs(R.string.app_name), state, onEvent)
    Column(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.coach) {
            Card(Modifier.fillMaxWidth(), "coach") {
                for ((i, line) in Lessons.coach.withIndex()) Line("${i + 1}. ${line.txt()}", "coach_$i", CtType.label, CtColors.TagInk)
                Key(rs(R.string.got_it), "coach_ok", R.drawable.ic_check, KeyKind.PRIMARY, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.CoachOk) }
            }
        } else {
            Line(rs(R.string.home_line), "home_line", CtType.titleSmall)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.weight(1f).fillMaxHeight(), "card_read") {
                // first run: the coach card takes the room, the keys say it already (v0.1.2: 64 dp keys)
                if (!state.coach) Line(rs(R.string.card_read), "card_read_t", CtType.title, CtColors.TagInk, 1)
                // while the coach card is up the cards are short: keep their keys whole, drop the sub-line
                if (!state.coach) Line(rs(R.string.card_read_sub), "card_read_s", CtType.body, CtColors.TagInk, 3)
                Key(rs(R.string.read_mine), "read_mine", R.drawable.ic_palette, KeyKind.PRIMARY, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.ReadMine) }
            }
            Card(Modifier.weight(1f).fillMaxHeight(), "card_virtual") {
                if (!state.coach) Line(rs(R.string.card_virtual), "card_virtual_t", CtType.title, CtColors.TagInk, 1)
                if (!state.coach) Line(rs(R.string.card_virtual_sub), "card_virtual_s", CtType.body, CtColors.TagInk, 3)
                // two keys stacked: side by side they clipped to 「跟着」「自己」 in the 280-dp card
                Key(rs(R.string.learn), "learn_virtual", R.drawable.ic_school, KeyKind.PRIMARY, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.LearnVirtual) }
                Key(rs(R.string.play), "play_virtual", R.drawable.ic_gamepad, KeyKind.LIGHT, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.PlayVirtual) }
            }
        }
        val l = state.lesson
        if (l != null && !l.solved) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(PicoTheme.colorScheme.fillTertiary).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Line(rs(R.string.resume_line, Lessons.info(l.stage).name.txt(), l.idx + 1, l.steps.size.coerceAtLeast(1)), "resume_line", CtType.body, modifier = Modifier.weight(1f))
                Key(rs(R.string.resume), "resume", R.drawable.ic_play_arrow, KeyKind.NORMAL, 64.dp) { onEvent(Ui.Resume) }
            }
        }
    }
}

@Composable
private fun ReadPanel(state: CtUiState, onEvent: (Ui) -> Unit) {
    val r = state.read ?: return
    val failure = r.failure
    TitleRow(rs(if (r.reconcile) R.string.reconcile_title else R.string.read_title), state, onEvent) {
        if (!r.reconcile && r.mode != ReadMode.CONFIRM) Line(rs(R.string.read_progress, r.face), "read_progress", CtType.button, lines = 1)
    }
    Column(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        when {
            r.fixing != null -> {
                // S06: 改成：6 colours + 取消 (+ 重扫这一面)
                Line(rs(R.string.fix_title), "fix_title", CtType.big)
                // a centre can't take another centre's colour (user addendum): say so right here
                if (r.centreClash) Card(Modifier.fillMaxWidth(), "centre_clash") { Line(rs(R.string.centre_clash), "centre_clash_line", CtType.body, CtColors.WarnInk, 2) }
                Palette(null, "fix") { c -> onEvent(Ui.FixColor(c)) }
                Key(rs(R.string.cancel), "fix_cancel", R.drawable.ic_close, KeyKind.NORMAL, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.FixCancel) }
                Key(rs(R.string.rescan_face), "fix_rescan", R.drawable.ic_replay, KeyKind.NORMAL, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.ReadFaceAgain) }
            }
            failure != null -> {
                // S07
                FaceChips(r, failure.suspects.map { Facelets.faceOf(it) }.toSet(), true, onEvent)
                Card(Modifier.fillMaxWidth(), "check_fail") {
                    Line(rs(R.string.check_fail_title), "check_title", CtType.title, CtColors.ErrInk)
                    Line(failText(failure), "check_body", CtType.body, CtColors.TagInk, 3)
                    Line(rs(R.string.check_fix_hint), "check_hint", CtType.label, CtColors.TagInk)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Key(rs(if (r.camOk) R.string.rescan_face else R.string.read_face_again), "read_face_again", R.drawable.ic_replay, KeyKind.PRIMARY, 64.dp, Modifier.weight(1f)) { onEvent(Ui.ReadFaceAgain) }
                    Key(rs(if (r.camOk) R.string.scan_from_start else R.string.read_from_start), "read_from_start", R.drawable.ic_restart_alt, KeyKind.NORMAL, 64.dp, Modifier.weight(1f)) { onEvent(Ui.ReadFromStart) }
                }
            }
            r.mode == ReadMode.UNAVAILABLE -> {
                // S05: one line why; → S04 in 2 s, faces read so far stay
                Card(Modifier.fillMaxWidth(), "cam_unavailable") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(R.drawable.ic_info), contentDescription = null, modifier = Modifier.size(32.dp), tint = CtColors.TagInk)
                        Spacer(Modifier.width(10.dp))
                        Line(rs(R.string.cam_title), "cam_title", CtType.big, CtColors.TagInk, 1)
                    }
                    Line(rs(if (r.issue == CamIssue.DENIED) R.string.cam_denied else R.string.cam_failed), "cam_why", CtType.title, CtColors.TagInk, 3)
                }
                Line(rs(R.string.cam_keep, r.scanned.size), "cam_keep", CtType.body, lines = 2)
                if (r.issue == CamIssue.DENIED) Key(rs(R.string.cam_ask_again), "cam_ask_again", R.drawable.ic_photo_camera, KeyKind.NORMAL, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.AskCameraAgain) }
                Key(rs(R.string.tap_colors_now), "cam_paint", R.drawable.ic_palette, KeyKind.PRIMARY, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.PaintAll) }
            }
            r.mode == ReadMode.CONFIRM -> ConfirmPanel(r, onEvent)
            r.mode == ReadMode.REVIEW -> {
                // S08 [去改] → tap an amber square on the cube (S06)
                FaceChips(r, r.amber.map { Facelets.faceOf(it) }.toSet(), true, onEvent)
                Card(Modifier.fillMaxWidth(), "review_card") { Line(rs(R.string.review_line), "review_line", CtType.title, CtColors.TagInk, 3) }
                Key(rs(R.string.review_done), "review_done", R.drawable.ic_check, KeyKind.PRIMARY, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.ReviewDone) }
            }
            r.reconcile && r.mode == ReadMode.PAINT -> {
                Line(rs(R.string.reconcile_body), "reconcile_body", CtType.body, lines = 3)
                Palette(r.paint, "palette") { c -> onEvent(Ui.Paint(c)) }
                Key(rs(R.string.reconcile_done), "reconcile_done", R.drawable.ic_check, KeyKind.PRIMARY, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.ReconcileDone) }
            }
            r.mode == ReadMode.PAINT -> {
                // S04: tap the colours (fallback)
                FaceChips(r, emptySet(), true, onEvent)
                Line(rs(R.string.frame_hint, colorName(CtController.readCube.centerColor(r.target))).replace("对进框里", "").let { _ -> Lessons.holdFor(r.target).txt() }, "read_how", CtType.titleSmall, lines = 2)
                Line(rs(R.string.paint_line), "paint_line", CtType.label, lines = 2)
                Palette(r.paint, "palette") { c -> onEvent(Ui.Paint(c)) }
                val filled = CtController.faceFilled(r.target)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (r.camOk || r.issue == CamIssue.DENIED) Key(rs(R.string.back_to_scan), "back_to_scan", R.drawable.ic_photo_camera, KeyKind.NORMAL, 64.dp) { onEvent(Ui.BackToScan) }
                    val last = r.paintOnly == null && r.scanned.size >= 5 && r.target !in r.scanned
                    Key(if (filled < 9) rs(R.string.face_missing, 9 - filled) else rs(if (!last) R.string.face_done else R.string.face_done_last), "face_done", R.drawable.ic_check, KeyKind.PRIMARY, 64.dp, Modifier.weight(1f), enabled = filled >= 9) { onEvent(Ui.FaceDone) }
                }
            }
            else -> {
                // S02 / S03: the camera reads; nothing to tap on the cube
                FaceChips(r, emptySet(), false, onEvent)
                val got = System.currentTimeMillis() - r.gotAt < 1000L
                val target = CtController.readCube.centerColor(r.target)
                val (line, color) =
                    when {
                        got -> rs(R.string.scan_got, r.scanned.size) to CtColors.Passable
                        r.guard == Guard.REPEAT -> rs(R.string.scan_repeat) to CtColors.WarnInk
                        r.tier >= 3 -> rs(R.string.scan_paint_now) to CtColors.WarnInk
                        r.tier == 2 -> rs(R.string.scan_face_camera) to CtColors.Interaction
                        r.askFace != null -> rs(R.string.frame_one_more, colorName(target)) to CtColors.Interaction
                        else -> rs(R.string.frame_hint, colorName(target)) to CtColors.TagInk
                    }
                // status inks (gold / green / amber) sit on the slate card: ≥ 4.5:1 there, not on the light glass
                Card(Modifier.fillMaxWidth(), "scan_status") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // what the camera sees now (same as the scan card by your hands; not tappable)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.probe("scan_live")) {
                            for (row in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                for (col in 0 until 3) {
                                    val c = r.live?.getOrNull(row * 3 + col) ?: CubeColor.NONE
                                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(colorOf(c)).then(if (got || r.progress > 0f) Modifier.border(2.dp, if (got) CtColors.Passable else CtColors.Interaction, RoundedCornerShape(8.dp)) else Modifier))
                                }
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Line(line, "scan_line", if (r.tier >= 2 && !got) CtType.title else CtType.titleSmall, color, 4)
                            Line(Lessons.holdFor(r.target).txt() + " · " + rs(R.string.frame_count, r.scanned.size), "read_how", CtType.body, CtColors.TagInk, 3)
                        }
                    }
                }
                if (r.tier < 2) Line(rs(R.string.scan_auto), "scan_auto", CtType.label, lines = 2)
                if (r.tier >= 3) Key(rs(R.string.scan_paint_face), "scan_paint_face", R.drawable.ic_palette, KeyKind.PRIMARY, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.PaintThisFace) }
                Key(rs(R.string.scan_paint_all), "scan_paint_all", R.drawable.ic_palette, KeyKind.NORMAL, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.PaintAll) }
            }
        }
    }
}

/**
 * User addendum 2026-11-11 (S08 confirm, before the virtual cube appears): the whole cube as a net (tap a face to turn
 * the 3D cube to it), striped = inferred, amber border = unsure; tap a square on the 3D cube to change it (S06), then
 * 对，就是这样 → the legality check.
 */
@Composable
private fun ConfirmPanel(r: ReadState, onEvent: (Ui) -> Unit) {
    CubeNet(r, onEvent)
    Card(Modifier.fillMaxWidth(), "confirm_card") {
        val parts = buildList {
            add(if (r.inferred.isNotEmpty()) rs(R.string.confirm_inferred, r.inferred.count { it % 9 != 4 }) else rs(R.string.confirm_all_read))
            if (r.amber.isNotEmpty()) add(rs(R.string.confirm_amber, r.amber.size))
        }
        Line(rs(R.string.confirm_title) + " " + parts.joinToString(" "), "confirm_marks", CtType.body, CtColors.TagInk, 3)
        Line(if (r.centreClash) rs(R.string.centre_clash) else rs(R.string.confirm_tap), "confirm_tap", CtType.label, if (r.centreClash) CtColors.WarnInk else CtColors.TagInk, 2)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Key(rs(R.string.confirm_yes), "confirm_yes", R.drawable.ic_check, KeyKind.PRIMARY, SHORT_KEY, Modifier.weight(1.5f)) { onEvent(Ui.ConfirmRead) }
        Key(rs(R.string.scan_from_start), "confirm_restart", R.drawable.ic_restart_alt, KeyKind.NORMAL, SHORT_KEY, Modifier.weight(1f)) { onEvent(Ui.ReadFromStart) }
    }
}

/** U on top, L F R B across, D below; each face row by row as you look at it (U's top = B, D's top = F). */
@Composable
private fun CubeNet(r: ReadState, onEvent: (Ui) -> Unit) {
    val layout = listOf(listOf(-1, 0, -1, -1), listOf(4, 2, 1, 5), listOf(-1, 3, -1, -1))
    // faces 78 dp (6.2 cm, 2.0° at 1.75 m) and 20 dp (1.6 cm) apart: D17 补 tap-target rule
    Column(Modifier.fillMaxWidth().probe("cube_net"), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in layout) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            for (f in row) {
                if (f < 0) {
                    Spacer(Modifier.size(78.dp))
                    continue
                }
                val now = f == r.target
                Column(
                    Modifier.size(78.dp).clip(RoundedCornerShape(10.dp)).background(if (now) CtColors.Interaction else CtColors.TagBg).clickable { onEvent(Ui.GoFace(Lessons.readFaces.indexOf(f) + 1)) }.padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (y in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (x in 0 until 3) {
                            val i = f * 9 + y * 3 + x
                            val c = CtController.readCube[i]
                            val inferred = i in r.inferred
                            val amber = i in r.amber
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(3.dp)).background(colorOf(c))
                                    .then(if (amber) Modifier.border(2.dp, CtColors.Interaction, RoundedCornerShape(3.dp)) else Modifier)
                                    .then(if (inferred) Modifier.drawBehind { stripes(this) } else Modifier),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Diagonal stripes on an inferred square (not scanned). */
private fun stripes(d: androidx.compose.ui.graphics.drawscope.DrawScope) {
    val w = d.size.width
    val h = d.size.height
    val ink = Color(0x8C1B1B1F) // design-style: fixed-figma-color stripe ink on stickers
    var x = -h
    while (x < w) {
        d.drawLine(ink, androidx.compose.ui.geometry.Offset(x, h), androidx.compose.ui.geometry.Offset(x + h, 0f), strokeWidth = w / 9f)
        x += w / 3f
    }
}

/** The six face chips (v1.5: replace the progress dots): centre colour, tick when read, ring = now, red dot = suspect. */
@Composable
private fun FaceChips(r: ReadState, flagged: Set<Int>, clickable: Boolean, onEvent: (Ui) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        for (n in 1..6) {
            val f = Lessons.readFaces[n - 1]
            val c = CtController.readCube.centerColor(f)
            val now = f == r.target
            val read = f in r.scanned
            Box(contentAlignment = Alignment.Center) {
                val ring = if (now) Modifier.border(4.dp, CtColors.Interaction, CircleShape) else Modifier
                val tick: @Composable () -> Unit = {
                    if (read) Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(24.dp), tint = if (c == CubeColor.BLUE || c == CubeColor.GREEN || c == CubeColor.RED) Color.White else CtColors.OnFill)
                }
                if (clickable) {
                    Button(
                        onClick = { onEvent(Ui.GoFace(n)) },
                        modifier = Modifier.size(64.dp).probe("chip_$n").then(ring),
                        size = ButtonDefaults.Regular.copy(height = 64.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = colorOf(c), contentColor = CtColors.OnFill),
                    ) { tick() }
                } else {
                    // while the camera reads, the chips only show progress (nothing to tap)
                    Box(Modifier.size(64.dp).probe("chip_$n").clip(CircleShape).background(colorOf(c)).then(ring), contentAlignment = Alignment.Center) { tick() }
                }
                if (f in flagged) Box(Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(CtColors.ErrInk))
            }
        }
    }
}

@Composable
private fun failText(f: Legality.Failure): String {
    val z = zh()
    val centers = rs(R.string.check_count_centers)
    return when (f.kind) {
        Legality.Kind.COUNT -> rs(R.string.check_count, f.counts.filter { it.value != 9 }.entries.joinToString(" · ") { "${if (z) it.key.zh else it.key.en} ${it.value}" }.ifEmpty { centers })
        Legality.Kind.PIECE -> rs(R.string.check_piece)
        Legality.Kind.PARITY -> rs(R.string.check_parity)
    }
}

@Composable
private fun ReadyPanel(state: CtUiState, onEvent: (Ui) -> Unit) {
    TitleRow(rs(R.string.ready_title), state, onEvent)
    if (state.readyAmber.isNotEmpty()) {
        // S08 with unsure squares: ask first (design readFlow.review)
        Centered {
            Card(Modifier.fillMaxWidth(), "amber_ask") {
                Line(rs(R.string.ready_line), "ready_line", CtType.title, CtColors.TagInk)
                Line(rs(R.string.amber_ask, state.readyAmber.size), "amber_ask", CtType.body, CtColors.TagInk, 3)
            }
            Key(rs(R.string.amber_ok), "amber_ok", R.drawable.ic_check, KeyKind.PRIMARY, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.AmberOk) }
            Key(rs(R.string.amber_fix), "amber_fix", R.drawable.ic_palette, KeyKind.NORMAL, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.AmberFix) }
        }
        return
    }
    Centered {
        Card(Modifier.fillMaxWidth(), "ready") {
            Line(rs(R.string.ready_line), "ready_line", CtType.title, CtColors.TagInk)
            Line(rs(R.string.ready_hold, colorName(state.firstFront ?: CubeColor.RED)), "ready_hold", CtType.body, CtColors.TagInk, 3)
        }
        Key(rs(R.string.start_real), "start_real", R.drawable.ic_school, KeyKind.PRIMARY, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.StartReal) }
        Key(rs(R.string.play), "ready_play", R.drawable.ic_gamepad, KeyKind.NORMAL, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.ReadyPlay) }
    }
}

// =================================================================================================== 复原课 Learn (S09–S15, S18, S27)

@Composable
fun LearnPage(state: CtUiState, onEvent: (Ui) -> Unit) {
    val l = state.lesson
    Column(Modifier.fillMaxSize()) {
        TitleRow(rs(R.string.tab_learn), state, onEvent) {
            if (l != null && !l.solved) HoldTag(state)
        }
        when {
            state.replaceAsk != null -> Centered { ReplaceConfirm(onEvent) }
            l == null -> Centered {
                // S18
                Card(Modifier.fillMaxWidth(), "no_cube") { Line(rs(R.string.no_cube), "no_cube", CtType.title, CtColors.TagInk, 3) }
                Key(rs(R.string.read_mine), "nc_read", R.drawable.ic_photo_camera, KeyKind.PRIMARY, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.ReadMine) }
                Key(rs(R.string.scramble_learn), "nc_scramble", R.drawable.ic_shuffle, KeyKind.NORMAL, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.LearnScramble) }
            }
            l.solved -> SolvedCard(rs(R.string.solved_lesson, l.turns), onEvent)
            state.why != null -> WhyPanel(state, onEvent)
            l.computing -> Centered { Line(rs(R.string.planning), "planning", CtType.title, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            l.stageDone -> StageDoneCard(state, onEvent)
            else -> LessonStep(state, onEvent)
        }
    }
}

@Composable
private fun HoldTag(state: CtUiState) {
    val c = state.cube
    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(CtColors.TagBg).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text = rs(R.string.hold_tag, colorName(c.centerColor(0)), colorName(c.centerColor(2))), style = CtType.label, color = CtColors.TagInk, maxLines = 1, onTextLayout = fits("learn/hold_tag"))
    }
}

/** Seven segments, the current one in interaction yellow (design: 七段进度条). */
@Composable
private fun StageBar(stage: Int) {
    Row(Modifier.fillMaxWidth().height(10.dp).probe("stage_bar"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in 1..7) {
            Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(when { k < stage -> CtColors.Passable; k == stage -> CtColors.Interaction; else -> PicoTheme.colorScheme.fillTertiary }))
        }
    }
}

@Composable
private fun LessonStep(state: CtUiState, onEvent: (Ui) -> Unit) {
    val l = state.lesson ?: return
    val info = Lessons.info(l.stage)
    val step = l.step ?: return
    Column(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        StageBar(l.stage)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Line("${l.stage}. ${info.name.txt()} · ${info.tag.txt()}", "stage_name", CtType.titleSmall, lines = 1, modifier = Modifier.weight(1f))
            }
        when {
            l.recomputed -> Line(rs(R.string.recomputed, l.stage), "recomputed", CtType.label)
            l.skipped.isNotEmpty() && l.idx == 0 -> Line(rs(R.string.skipped, Lessons.info(l.skipped.last()).name.txt()), "skipped", CtType.label)
        }
        if (l.offScript != null) {
            // S27
            Card(Modifier.fillMaxWidth(), "offscript") {
                Line(rs(R.string.offscript, l.offScript.did?.let { Lessons.caption(it).txt() } ?: rs(R.string.another_layer), Lessons.caption(l.offScript.want).txt()), "offscript_line", CtType.title, CtColors.WarnInk, 3)
            }
            Key(rs(R.string.undo), "off_undo", R.drawable.ic_undo, KeyKind.PRIMARY, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.Undo) }
            Key(rs(R.string.keep_recompute), "off_keep", R.drawable.ic_restart_alt, KeyKind.NORMAL, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.Recompute) }
            return@Column
        }
        val correct = l.correctAt > 0 && System.currentTimeMillis() - l.correctAt < 600
        val big = if (l.undoMove != null) rs(R.string.undo_real, Lessons.caption(l.undoMove).txt()) else Lessons.caption(step.move).txt()
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (correct) Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(32.dp), tint = CtColors.Passable)
            Line(big, "big_caption", CtType.big, lines = 2, modifier = Modifier.weight(1f))
        }
        state.lessonCue?.let { (h, p) -> Pill(if (l.halfTurn != null) rs(R.string.hint_half) else howTo(h, p), "how_to") }
        Line(Lessons.reason(step.reason).txt(), "reason", CtType.body, lines = 2)
        Line(rs(R.string.step_of, l.idx + 1, l.steps.size), "step_of", CtType.label, lines = 1)
        if (l.mode == LessonMode.REAL) {
            Key(rs(R.string.turned_it), "turned_it", R.drawable.ic_check, KeyKind.PRIMARY, 72.dp, Modifier.fillMaxWidth()) { onEvent(Ui.TurnedIt) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Key(rs(R.string.show_again), "show_again", R.drawable.ic_replay, KeyKind.NORMAL, 64.dp, Modifier.weight(1f)) { onEvent(Ui.ShowAgain) }
                Key(rs(R.string.undo), "undo", R.drawable.ic_undo, KeyKind.NORMAL, 64.dp, Modifier.weight(1f), enabled = state.history.isNotEmpty()) { onEvent(Ui.Undo) }
                Key(rs(R.string.why), "why", R.drawable.ic_help, KeyKind.NORMAL, 64.dp, Modifier.weight(1f)) { onEvent(Ui.Why) }
            }
        } else {
            Line(rs(R.string.virtual_line), "virtual_line", CtType.label, lines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Key(rs(R.string.help_turn), "help_turn", R.drawable.ic_play_arrow, KeyKind.PRIMARY, 64.dp, Modifier.weight(1.2f)) { onEvent(Ui.HelpTurn) }
                Key(rs(R.string.undo), "undo", R.drawable.ic_undo, KeyKind.NORMAL, 64.dp, Modifier.weight(1f), enabled = state.history.isNotEmpty()) { onEvent(Ui.Undo) }
                Key(rs(R.string.why), "why", R.drawable.ic_help, KeyKind.NORMAL, 64.dp, Modifier.weight(1f)) { onEvent(Ui.Why) }
            }
        }
        Line(hintText(state) ?: rs(R.string.learn_hint), "learn_hint", CtType.label, PicoTheme.colorScheme.labelSecondary, 2)
    }
}

@Composable
private fun WhyPanel(state: CtUiState, onEvent: (Ui) -> Unit) {
    val w = state.why ?: return
    val info = Lessons.info(w.stage)
    Column(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Line(rs(R.string.why_title, info.name.txt()), "why_title", CtType.title, lines = 1)
        Card(Modifier.fillMaxWidth(), "why_card") { Line(info.why.txt(), "why_body", CtType.body, CtColors.TagInk, 4) }
        val cap = info.demo.getOrNull(w.step)
        Pill(if (w.step < 0) rs(R.string.why_wait) else rs(R.string.why_step, w.step + 1, info.demo.size, cap?.second?.txt() ?: ""), "why_step")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Key(rs(R.string.why_again), "why_again", R.drawable.ic_replay, KeyKind.NORMAL, SHORT_KEY, Modifier.weight(1f)) { onEvent(Ui.WhyAgain) }
            Key(rs(R.string.why_ok), "why_ok", R.drawable.ic_check, KeyKind.PRIMARY, SHORT_KEY, Modifier.weight(1f)) { onEvent(Ui.WhyOk) }
        }
    }
}

@Composable
private fun StageDoneCard(state: CtUiState, onEvent: (Ui) -> Unit) {
    val l = state.lesson ?: return
    val info = Lessons.info(l.stage)
    Centered {
        StageBar(l.stage + 1)
        Card(Modifier.fillMaxWidth(), "stage_done") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(30.dp), tint = CtColors.Passable)
                Spacer(Modifier.width(8.dp))
                Line(info.doneTitle.txt(), "done_title", CtType.title, CtColors.TagInk, 1)
            }
            Line(info.goal.txt(), "done_goal", CtType.body, CtColors.TagInk, 3)
            if (l.mode == LessonMode.REAL) Line(rs(R.string.like_this), "like_this", CtType.titleSmall, CtColors.TagInk)
        }
        if (l.mode == LessonMode.REAL) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Key(rs(R.string.yes_next), "yes_next", R.drawable.ic_check, KeyKind.PRIMARY, SHORT_KEY, Modifier.weight(1.3f)) { onEvent(Ui.NextStage) }
                Key(rs(R.string.not_same), "not_same", R.drawable.ic_photo_camera, KeyKind.NORMAL, SHORT_KEY, Modifier.weight(1f)) { onEvent(Ui.NotSame) }
            }
        } else {
            Key(rs(R.string.next_stage), "next_stage", R.drawable.ic_chevron_right, KeyKind.PRIMARY, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.NextStage) }
        }
    }
}

@Composable
private fun SolvedCard(detail: String, onEvent: (Ui) -> Unit) {
    Centered {
        Card(Modifier.fillMaxWidth(), "solved") {
            Line(rs(R.string.solved), "solved_title", CtType.big, CtColors.TagInk, 1)
            Line(detail, "solved_detail", CtType.body, CtColors.TagInk, 2)
            Line(rs(R.string.solved_small), "solved_small", CtType.label, CtColors.TagInk, 2)
        }
        Key(rs(R.string.again_round), "again_round", R.drawable.ic_shuffle, KeyKind.PRIMARY, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.AgainRound) }
        Key(rs(R.string.read_next), "read_next", R.drawable.ic_photo_camera, KeyKind.NORMAL, SHORT_KEY, Modifier.fillMaxWidth()) { onEvent(Ui.ReadNext) }
    }
}

// =================================================================================================== 自由玩 Play (S16 / S17 / S23–S26 / S15)

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

@Composable
fun PlayPage(state: CtUiState, onEvent: (Ui) -> Unit) {
    val p = state.play
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p.startMs, p.solved) {
        while (p.startMs > 0 && !p.solved) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TitleRow(rs(R.string.tab_play), state, onEvent)
        when {
            state.replaceAsk != null -> Centered { ReplaceConfirm(onEvent) }
            p.solved -> SolvedCard(rs(R.string.solved_play, clock(p.endMs - p.startMs), p.moves) + if (p.newBest) "  ·  " + rs(R.string.new_best) else "", onEvent)
            else -> Column(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                Options(rs(R.string.scramble_label), listOf(6 to rs(R.string.lv_easy), 15 to rs(R.string.lv_normal), 25 to rs(R.string.lv_full)), p.level, "level", 130.dp) { onEvent(Ui.Level(it)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val elapsed = if (p.startMs > 0) now - p.startMs else 0L
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(CtColors.TagBg).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(text = rs(R.string.timer, clock(elapsed), p.moves), style = CtType.button, color = CtColors.TagInk, maxLines = 1, onTextLayout = fits("play/timer"))
                    }
                    Line(if (state.prefs.bestMs > 0) rs(R.string.best, clock(state.prefs.bestMs), state.prefs.bestMoves) else rs(R.string.best_none), "best", CtType.label, lines = 1, modifier = Modifier.weight(1f))
                }
                Key(rs(if (p.scrambled) R.string.rescramble else R.string.scramble), "scramble", R.drawable.ic_shuffle, KeyKind.PRIMARY, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.Scramble) }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Key(rs(R.string.undo), "play_undo", R.drawable.ic_undo, KeyKind.NORMAL, 64.dp, Modifier.weight(1f), enabled = state.history.isNotEmpty()) { onEvent(Ui.Undo) }
                    Key(rs(R.string.to_lesson), "to_lesson", R.drawable.ic_school, KeyKind.NORMAL, 64.dp, Modifier.weight(1.3f), enabled = !state.cube.isSolved) { onEvent(Ui.ToLesson) }
                }
                Line(rs(R.string.picked, selectionText(state.selection)), "picked", CtType.titleSmall, PicoTheme.colorScheme.labelPrimary, 1)
                Line(hintText(state) ?: rs(R.string.play_hint), "play_hint", CtType.label, PicoTheme.colorScheme.labelSecondary, 2)
            }
        }
    }
}

// =================================================================================================== 设置 Settings (S20 / S21)

@Composable
fun ColumnScope.SettingsPage(state: CtUiState, onEvent: (Ui) -> Unit) {
    val p = state.prefs
    fun pr(why: String, f: (com.kejing.cubetutor.ui.Prefs) -> com.kejing.cubetutor.ui.Prefs) = onEvent(Ui.SetPrefs(why, f))
    TitleRow(rs(R.string.tab_settings), state, onEvent)
    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        // volume: slider 0–100 % snapping to low / mid / high (40 / 70 / 100 within ±6) + percent + mute switch (D15, B20)
        var vol by remember(p.volume) { mutableFloatStateOf(p.volume.toFloat()) }
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(R.drawable.ic_volume_up), contentDescription = null, modifier = Modifier.size(22.dp), tint = PicoTheme.colorScheme.labelPrimary)
            Spacer(Modifier.width(10.dp))
            Text(text = rs(R.string.volume), style = CtType.body, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, modifier = Modifier.width(80.dp), onTextLayout = fits("settings/volume"))
            Slider(value = vol, onValueChange = { vol = it }, modifier = Modifier.weight(1f).height(48.dp).probe("volume"), valueRange = 0f..100f, onValueChangeFinished = {
                val snapped = listOf(40f, 70f, 100f).firstOrNull { kotlin.math.abs(it - vol) <= 6f } ?: vol
                vol = snapped
                pr("volume") { it.copy(volume = snapped.roundToInt()) }
            })
            Text(text = String.format(Locale.US, "%d%%", vol.roundToInt()), style = CtType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, modifier = Modifier.width(52.dp).padding(start = 8.dp), onTextLayout = fits("settings/volume_pct"))
            Switch(checked = !p.muted, onCheckedChange = { on -> pr("mute") { it.copy(muted = !on) } }, modifier = Modifier.height(48.dp).probe("mute_switch"))
        }
        if (state.ask == SettingsAsk.NONE) Options(rs(R.string.volume_steps), listOf(40 to rs(R.string.vol_low), 70 to rs(R.string.vol_mid), 100 to rs(R.string.vol_high)), listOf(40, 70, 100).minByOrNull { kotlin.math.abs(it - p.volume) } ?: 70, "vol_step", 104.dp) { v -> pr("volume") { it.copy(volume = v) } }
        Options(rs(R.string.language), listOf(Lang.SYSTEM to rs(R.string.lang_auto), Lang.ZH to rs(R.string.lang_zh), Lang.EN to rs(R.string.lang_en)), p.lang, "lang", 120.dp) { l -> pr("lang") { it.copy(lang = l) } }
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Key(rs(R.string.recenter), "recenter", R.drawable.ic_my_location, KeyKind.NORMAL, 64.dp, Modifier.weight(1f)) { onEvent(Ui.Recenter) }
            Key(rs(R.string.reset_progress), "reset_progress", R.drawable.ic_restart_alt, KeyKind.NORMAL, 64.dp, Modifier.weight(1f)) { onEvent(Ui.Ask(SettingsAsk.RESET)) }
        }
        when (state.ask) {
            SettingsAsk.RESET, SettingsAsk.RESET_BEST -> Card(Modifier.fillMaxWidth(), "reset_confirm") {
                Line(rs(if (state.ask == SettingsAsk.RESET) R.string.reset_q else R.string.reset_best_q), "reset_q", CtType.body, CtColors.TagInk)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Key(rs(R.string.cancel), "reset_cancel", null, KeyKind.LIGHT, 64.dp, Modifier.weight(1f)) { onEvent(Ui.Ask(SettingsAsk.NONE)) }
                    Key(rs(R.string.reset_yes), "reset_yes", null, KeyKind.DANGER, 64.dp, Modifier.weight(1f)) { onEvent(if (state.ask == SettingsAsk.RESET) Ui.ResetYes else Ui.ResetBestYes) }
                }
            }
            else -> Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Key(rs(R.string.reset_best), "reset_best", R.drawable.ic_restart_alt, KeyKind.NORMAL, 64.dp, Modifier.weight(1f)) { onEvent(Ui.Ask(SettingsAsk.RESET_BEST)) }
                Line(rs(R.string.about, BuildConfig.VERSION_NAME), "about", CtType.label, lines = 2, modifier = Modifier.weight(1f))
            }
        }
        // D26: quit last; the confirmation opens right here (S21)
        if (state.ask == SettingsAsk.EXIT) {
            Card(Modifier.fillMaxWidth(), "exit_confirm") {
                Line(rs(R.string.exit_q), "exit_q", CtType.title, CtColors.TagInk)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Key(rs(R.string.cancel), "quit_cancel", null, KeyKind.LIGHT, 64.dp, Modifier.weight(1f)) { onEvent(Ui.Ask(SettingsAsk.NONE)) }
                    Key(rs(R.string.exit_yes), "quit_yes", R.drawable.ic_logout, KeyKind.DANGER, 64.dp, Modifier.weight(1f)) { onEvent(Ui.QuitYes) }
                }
            }
        } else {
            Key(rs(R.string.quit), "quit", R.drawable.ic_logout, KeyKind.DANGER, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.Ask(SettingsAsk.EXIT)) }
        }
    }
}
