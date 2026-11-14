package com.kejing.cubetutor.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.kejing.cubetutor.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
