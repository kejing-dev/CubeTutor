package com.kejing.cubetutor.ui.theme

import androidx.compose.ui.graphics.Color

/** The design's colours (design-package theme.appTokens + the window keys). The only file with colour literals. */
object CtColors {
    // keys on the window glass (PicoTheme roles stay the system's own, design theme.ui)
    val Interaction = Color(0xFFF2B33D) // design-style: fixed-figma-color turnArc gold, main key / lesson cue
    val Passable = Color(0xFF2EB872) // design-style: fixed-figma-color correctGlow green
    val OnFill = Color(0xFF1B1B1F) // design-style: fixed-figma-color ink on gold / green keys
    val DangerFill = Color(0xFFFF8A85) // design-style: fixed-figma-color quit / clear keys
    val OnError = Color(0xFF0A0D10) // design-style: fixed-figma-color ink on the danger fill

    // cards and pills: opaque dark slate with light text
    val TagBg = Color(0xFF1E2A33) // design-style: fixed-figma-color card background
    val TagInk = Color(0xFFE6F1F7) // design-style: fixed-figma-color card text
    val WarnBg = Color(0xFF4A2E0A) // design-style: fixed-figma-color amber card
    val WarnInk = Color(0xFFFFD9A8) // design-style: fixed-figma-color amber text
    val ErrInk = Color(0xFFFFC5C5) // design-style: fixed-figma-color red text on slate

    // stickers (theme.appTokens)
    val StickerWhite = Color(0xFFF4F4EF) // design-style: fixed-figma-color
    val StickerYellow = Color(0xFFFFD500) // design-style: fixed-figma-color
    val StickerRed = Color(0xFFC8102E) // design-style: fixed-figma-color
    val StickerOrange = Color(0xFFFF6A13) // design-style: fixed-figma-color
    val StickerBlue = Color(0xFF0046AD) // design-style: fixed-figma-color
    val StickerGreen = Color(0xFF009B48) // design-style: fixed-figma-color
    val Unfilled = Color(0xFF6B7078) // design-style: fixed-figma-color not read yet (mid grey: the light grey read as white)
    val Body = Color(0xFF1B1B1F) // design-style: fixed-figma-color cube plastic between faces (S2 stickerless)
    val Suspect = Color(0xFFFF4D4D) // design-style: fixed-figma-color suspectRing
    val HighlightRim = Color(0xFFFFFFFF) // design-style: fixed-figma-color selected layer outline

    // handle rows (design input.handles: 行 = 蓝灰、列 = 暖橙、前后 = 青绿)
    val RowBlueGrey = Color(0xFF7D93B2) // design-style: fixed-figma-color
    val ColWarmOrange = Color(0xFFE8925A) // design-style: fixed-figma-color
    val DepthTeal = Color(0xFF3FB5A8) // design-style: fixed-figma-color
    val HandleBg = Color(0xFF242A31) // design-style: fixed-figma-color handle plate
    val Tray = Color(0xFF2A2F36) // design-style: fixed-figma-color tray strip
}
