package com.kejing.cubetutor.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kejing.cubetutor.platform.CtLog

/**
 * Debug builds only. Forwards adb broadcasts to the running app (action `com.kejing.cubetutor.DEBUG`); see
 * [DebugCommands] and AGENTS.md for the commands.
 *
 *   adb -s emulator-5554 shell "am broadcast -a com.kejing.cubetutor.DEBUG -p com.kejing.cubetutor --es cmd 'scramble=15 seed=7'"
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val raw = intent.getStringExtra("cmd") ?: return
        CtLog.i("CMD recv '$raw'")
        // broadcasts arrive on the main thread, where the controller and the scene live; a bad command logs, never crashes
        runCatching { CtLog.i("CMD '$raw' -> ${DebugCommands.run(context.applicationContext, raw)}") }.onFailure { CtLog.w("CMD '$raw' failed: $it") }
    }
}
