package com.kejing.cubetutor.ui

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.kejing.cubetutor.platform.CtLog
import com.kejing.cubetutor.ui.components.CtStage
import com.kejing.cubetutor.ui.components.LocalCtResources
import com.kejing.cubetutor.ui.components.localizedResources
import com.kejing.cubetutor.ui.debug.Probe
import com.kejing.cubetutor.ui.scene.CubeFrameSystem
import com.kejing.cubetutor.ui.scene.CubeScene
import com.kejing.cubetutor.ui.window.WindowCtl

/**
 * The Stage screen: wires [CtController] (state), [CubeScene] (entities, per-frame work) and [CtStage], and opens the
 * control window through the level gate (design lifecycle: openStage(Mixed) → head pose → level → window + rig).
 */
@Composable
fun CtScreen() {
    val context = LocalContext.current
    remember(context) {
        Probe.enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        CtController.start(context)
        true
    }
    val state by CtController.state.collectAsStateWithLifecycle()
    val scene = remember { CubeScene() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(scene, lifecycle) {
        registerSystem<CubeFrameSystem>()
        Probe.host = object : Probe.Host { override fun scene(): CubeScene = scene }
        // background: stop the controller polling and the sound, save (design lifecycle); back: resume
        val observer =
            LifecycleEventObserver { _, event ->
                CtLog.i("LIFECYCLE event=$event")
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        scene.pause()
                        CtController.onBackground(true)
                    }
                    Lifecycle.Event.ON_START -> {
                        scene.resume()
                        CtController.onBackground(false)
                    }
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        CtLog.i("STAGE enter")
        onDispose {
            lifecycle.removeObserver(observer)
            Probe.host = null
            unregisterSystem<CubeFrameSystem>()
            scene.detach()
            CtLog.i("STAGE disposed")
        }
    }

    val navigator = LocalSpatialNavigator.current
    DisposableEffect(navigator) {
        WindowCtl.navigator = navigator
        onDispose { if (WindowCtl.navigator === navigator) WindowCtl.navigator = null }
    }
    LaunchedEffect(Unit) { WindowCtl.openWindow("stage") }

    val resources = remember(state.prefs.lang, context) { localizedResources(context, state.prefs.lang) }
    CompositionLocalProvider(LocalCtResources provides resources) {
        CtStage(state = state, scene = scene)
    }
}
