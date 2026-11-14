package com.kejing.cubetutor.platform

import android.content.Context
import com.kejing.cubetutor.ui.Lang
import com.kejing.cubetutor.ui.Prefs
import java.io.File

/**
 * Local persistence (design dataModel): settings in SharedPreferences; the cube and the lesson in one JSON file written
 * through a temp file + atomic rename (Cloud Air SaveCodec style) after every turn and every read face. Offline only.
 */
class Store(private val context: Context) {
    private val p = context.getSharedPreferences("cube_tutor", Context.MODE_PRIVATE)
    private val file: File
        get() = File(context.filesDir, "save.json")

    fun prefs(): Prefs {
        val d = Prefs()
        return Prefs(
            volume = p.getInt("volume", d.volume),
            muted = p.getBoolean("muted", d.muted),
            lang = runCatching { Lang.valueOf(p.getString("lang", d.lang.name)!!) }.getOrDefault(d.lang),
            coachSeen = p.getBoolean("coachSeen", d.coachSeen),
            bestMs = p.getLong("bestMs", 0L),
            bestMoves = p.getInt("bestMoves", 0),
        )
    }

    fun savePrefs(v: Prefs) {
        p.edit().putInt("volume", v.volume).putBoolean("muted", v.muted).putString("lang", v.lang.name).putBoolean("coachSeen", v.coachSeen)
            .putLong("bestMs", v.bestMs).putInt("bestMoves", v.bestMoves).apply()
    }

    /** The saved session (cube code, lesson mode / stage / the cube's first-read front colour), or null. */
    fun loadSave(): Saved? =
        runCatching {
            if (!file.exists()) return null
            val o = org.json.JSONObject(file.readText())
            Saved(
                cube = o.getString("cube"),
                hasCube = o.optBoolean("hasCube", true),
                lessonMode = o.optString("lessonMode", "").ifEmpty { null },
                scrambleLevel = o.optInt("scramble", 15),
            )
        }.getOrNull()

    fun save(s: Saved) {
        runCatching {
            val o = org.json.JSONObject()
            o.put("cube", s.cube)
            o.put("hasCube", s.hasCube)
            o.put("lessonMode", s.lessonMode ?: "")
            o.put("scramble", s.scrambleLevel)
            val tmp = File(context.filesDir, "save.json.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { CtLog.w("SAVE failed: $it") }
    }

    fun clearSave() {
        runCatching { file.delete() }
    }

    fun clearAll() {
        p.edit().clear().apply()
        clearSave()
    }

    data class Saved(val cube: String, val hasCube: Boolean, val lessonMode: String?, val scrambleLevel: Int)
}
