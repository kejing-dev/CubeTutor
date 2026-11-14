package com.kejing.cubetutor.ui.components

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.kejing.cubetutor.ui.Lang
import java.util.Locale

/**
 * Copy for the language chosen in the app (follow system / 中 / EN), switched live (Rehearsal Hall Strings.kt): the
 * screen provides a Resources object for that locale and every panel reads its strings from it.
 */
val LocalCtResources = staticCompositionLocalOf<Resources> { error("LocalCtResources not provided") }

fun localizedResources(context: Context, lang: Lang): Resources {
    val tag =
        when (lang) {
            Lang.SYSTEM -> return context.resources
            Lang.ZH -> "zh-CN"
            Lang.EN -> "en"
        }
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(Locale.forLanguageTag(tag))
    return context.createConfigurationContext(configuration).resources
}

@Composable
@ReadOnlyComposable
fun rs(@StringRes id: Int): String = LocalCtResources.current.getString(id)

@Composable
@ReadOnlyComposable
fun rs(@StringRes id: Int, vararg args: Any): String = LocalCtResources.current.getString(id, *args)

/** The control window (design container.controlWindow: 640 × 600 dp). */
object PanelSizes {
    const val WINDOW_W = 640f
    const val WINDOW_H = 600f
}
