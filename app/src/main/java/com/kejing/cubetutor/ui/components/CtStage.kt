package com.kejing.cubetutor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.geometry.Offset3D
import com.kejing.cubetutor.R
import com.kejing.cubetutor.domain.Vec3
import com.kejing.cubetutor.domain.CubeColor
import com.kejing.cubetutor.domain.Lessons
import com.kejing.cubetutor.domain.scan.Guard
import com.kejing.cubetutor.ui.CtUiState
import com.kejing.cubetutor.ui.debug.LocalProbePanel
import com.kejing.cubetutor.ui.input.BoardInputListener
import com.kejing.cubetutor.ui.input.CubeTarget
import com.kejing.cubetutor.ui.input.InputSource
import com.kejing.cubetutor.ui.input.Offset3M
import com.kejing.cubetutor.ui.input.boardInput
import com.kejing.cubetutor.ui.scene.CubeScene
import com.kejing.cubetutor.ui.scene.PanelIds
import com.kejing.cubetutor.ui.theme.CtColors
import com.kejing.cubetutor.ui.theme.CtType
import com.kejing.cubetutor.ui.window.WindowCtl

/**
 * The Stage content: one full-size SpatialView with the cube rig (built by [CubeScene]) and two small attachment panels
 * (the "打开面板" chip over the rig, the one-line hint on the line of sight). 3D taps / drags come in through the factory
 * board-input template (hand ray, eye-hand, controller pointer; short drags count as taps, D50); the app-drawn
 * controller ray goes through [CubeScene.onTarget] too. Everything else is in the system window.
 */
@Composable
internal fun CtStage(state: CtUiState, scene: CubeScene) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bindTick = scene.bindTick.intValue
    val density = LocalDensity.current
    val listener =
        object : BoardInputListener<CubeTarget> {
            override fun onTap(target: CubeTarget?, source: InputSource, position: Offset3D?) {
                if (target != null) scene.onTarget(target, source.label)
            }

            override fun onDragStart(target: CubeTarget, source: InputSource): Boolean {
                if (target != CubeTarget.Tray) return false
                scene.startDrag(source.label)
                return true
            }

            override fun onDragMove(totalM: Offset3M) = scene.moveDrag(Vec3(totalM.x, totalM.y, totalM.z))

            override fun onDragEnd() = scene.endDrag("gesture")

            override fun onDragCancel() = scene.endDrag("gesture-cancel")

            // system HoverEffect draws on the handle / sticker meshes; the app has nothing extra to show (README: hover
            // events do not reach apps on PICO OS 6.2)
            override fun onHover(target: CubeTarget?, source: InputSource) = Unit
        }
    SpatialView(
        modifier = Modifier.fillMaxSize().boardInput(CubeScene.RESOLVER, listener),
        attachments = {
            AttachmentPanel(PanelIds.CHIP, size = density.px(CubeScene.CHIP_W, CubeScene.CHIP_H)) { CompositionLocalProvider(LocalProbePanel provides PanelIds.CHIP) { OpenChip(scene.panelShown[PanelIds.CHIP] == true) } }
            AttachmentPanel(PanelIds.HINT, size = density.px(CubeScene.HINT_W, CubeScene.HINT_H)) { CompositionLocalProvider(LocalProbePanel provides PanelIds.HINT) { StageHint(state, scene.panelShown[PanelIds.HINT] == true) } }
            AttachmentPanel(PanelIds.SCAN, size = density.px(CubeScene.SCAN_W, CubeScene.SCAN_H)) { CompositionLocalProvider(LocalProbePanel provides PanelIds.SCAN) { ScanCard(state, scene.panelShown[PanelIds.SCAN] == true) } }
        },
        update = { _, attachments ->
            @Suppress("UNUSED_EXPRESSION") bindTick
            scene.bindPanels(attachments)
        },
        initial = { content, attachments -> scene.attach(content, attachments, context) },
    )
}

/** Attachment sizes are pixels: the dp size of the panel at the composition's density (q1-1: never larger than the chip). */
private fun Density.px(wDp: Float, hDp: Float): IntSize = IntSize(wDp.dp.roundToPx(), hDp.dp.roundToPx())

/** "打开面板" (S22): re-opens the window (a minimised one is restored where it was). */
@Composable
private fun OpenChip(inView: Boolean) {
    if (!inView) return
    // q1-1: the key is exactly the 12 x 4.5 cm chip (200 x 76 dp at PANEL_SCALE), never the attachment's max size
    Box(Modifier.size(CubeScene.CHIP_W.dp, CubeScene.CHIP_H.dp), contentAlignment = Alignment.Center) {
        Key(rs(R.string.open_panel), "open_panel", R.drawable.ic_settings, KeyKind.PRIMARY, 64.dp, Modifier.width((CubeScene.CHIP_W - 8f).dp)) { WindowCtl.reopen("chip") }
    }
}

/** 准备中 (S00) / 平视前方，面板就出来 (S28) / 看正前方，别动 (S30). */
@Composable
private fun StageHint(state: CtUiState, inView: Boolean) {
    if (!inView) return
    val text = when {
        WindowCtl.recentering -> rs(R.string.look_ahead)
        WindowCtl.levelHint -> rs(R.string.level_hint)
        else -> rs(R.string.loading)
    }
    Box(Modifier.size(CubeScene.HINT_W.dp, CubeScene.HINT_H.dp).clip(RoundedCornerShape(40.dp)).background(CtColors.TagBg).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = CtType.title, color = CtColors.TagInk, maxLines = 1, textAlign = TextAlign.Center, onTextLayout = fits("stage/hint"))
    }
}

/**
 * The alignment frame (user addendum 2026-11-11, S02 / S03): a 3 x 3 box the face goes into, with grid ticks; grey = no
 * cube, amber = found but not steady / too far / turned, green with a short fill = taken; the colours read so far sit in
 * the cells. Above it: which side to put in (by its centre colour), how to hold it, and the G6 prompts.
 */
@Composable
private fun ScanCard(state: CtUiState, inView: Boolean) {
    if (!inView) return
    val r = state.read ?: return
    // S03 (user 2026-11-17): the face just read stays shown, green, until it is confirmed in the window
    val got = r.mode == com.kejing.cubetutor.ui.ReadMode.FACE_OK
    val found = r.live != null
    val frame =
        when {
            got -> CtColors.Passable
            found -> CtColors.Interaction
            else -> CtColors.Unfilled
        }
    val fill by androidx.compose.animation.core.animateFloatAsState(if (got) 0.45f else 0f, androidx.compose.animation.core.tween(300), label = "lock")
    val center = colorName(com.kejing.cubetutor.ui.CtController.readCube.centerColor(r.target))
    val (line, color) =
        when {
            got -> rs(R.string.frame_check) to CtColors.Passable
            r.guard == Guard.REPEAT -> rs(R.string.scan_repeat) to CtColors.WarnInk
            r.guard == Guard.WRONG_SIDE -> rs(R.string.scan_wrong_side, center) to CtColors.WarnInk
            r.tier >= 3 -> rs(R.string.scan_paint_now_short) to CtColors.WarnInk
            r.tier == 2 -> rs(R.string.scan_face_camera_short) to CtColors.Interaction
            r.askFace != null -> rs(R.string.frame_one_more, center) to CtColors.Interaction
            else -> rs(R.string.frame_hint, center) to CtColors.TagInk
        }
    val sub =
        when {
            got -> rs(R.string.frame_count, r.scanned.size)
            found && r.quality == com.kejing.cubetutor.domain.scan.GridFinder.Quality.FAR -> rs(R.string.frame_far)
            found && r.quality == com.kejing.cubetutor.domain.scan.GridFinder.Quality.TILTED -> rs(R.string.frame_turning)
            found -> rs(R.string.frame_hold)
            else -> Lessons.holdFor(r.target).let { if (zh()) it.zh else it.en } + " · " + rs(R.string.frame_count, r.scanned.size)
        }
    Column(Modifier.size(CubeScene.SCAN_W.dp, CubeScene.SCAN_H.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)).background(CtColors.TagBg).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = line, style = CtType.button, color = color, maxLines = 2, textAlign = TextAlign.Center, onTextLayout = fits("frame/line"))
            Text(text = sub, style = CtType.label, color = CtColors.TagInk, maxLines = 2, textAlign = TextAlign.Center, onTextLayout = fits("frame/sub"))
        }
        // the box: 160 dp = 5.7 cm at FRAME_K
        Box(Modifier.size(CubeScene.FRAME_BOX_DP.dp).border(5.dp, frame, RoundedCornerShape(10.dp)).padding(9.dp)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until 3) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until 3) {
                        val k = row * 3 + col
                        val c = if (got) com.kejing.cubetutor.ui.CtController.readCube[r.target * 9 + k] else r.live?.getOrNull(k)
                        val amber = got && (r.target * 9 + k) in r.amber
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(5.dp))
                                .border(1.5.dp, frame.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
                                .background(if (c != null) colorOf(c).copy(alpha = 0.85f) else androidx.compose.ui.graphics.Color.Transparent)
                                .then(if (amber) Modifier.border(3.dp, CtColors.Interaction, RoundedCornerShape(5.dp)) else Modifier),
                        )
                    }
                }
            }
            if (fill > 0f) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(CtColors.Passable.copy(alpha = fill)))
        }
    }
}
