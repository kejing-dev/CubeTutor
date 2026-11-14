package com.kejing.cubetutor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme

/**
 * The system scheme as is (design theme.ui) with Noto Sans / Noto Sans SC.
 */
@Composable
fun CtTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // design theme.ui: all 16 PicoTheme roles inherit the system's (no app colour scheme); only the type family changes
    val system = systemColorScheme(context)
    val family = rememberCtFontFamily()
    val base = PicoTheme.typography
    val typography = remember(base, family) { base.withFamily(family) }
    PicoTheme(colorScheme = system, typography = typography, content = content)
}
