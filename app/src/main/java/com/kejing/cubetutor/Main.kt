package com.kejing.cubetutor

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.kejing.cubetutor.ui.CtScreen
import com.kejing.cubetutor.ui.components.PanelSizes
import com.kejing.cubetutor.ui.theme.CtTheme
import com.kejing.cubetutor.ui.window.CtWindow
import com.kejing.cubetutor.ui.window.PANEL_ID

/**
 * App entry (design container STAGE_MIXED): the default Stage is Mixed (manifest pico.spatial.stage.style=1) so you see the
 * real cube in your hand; all 2D content is one system Planar window opened from the Stage (D8 / D21, native-window-move);
 * the system moves / closes / minimises it.
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage { CtTheme { CtScreen() } }
        WindowContainer(
            PANEL_ID,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = PanelSizes.WINDOW_W.dp, height = PanelSizes.WINDOW_H.dp),
        ) { CtWindow() }
    }
