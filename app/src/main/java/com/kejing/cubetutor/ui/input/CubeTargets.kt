package com.kejing.cubetutor.ui.input

import com.kejing.cubetutor.domain.Facelets
import com.kejing.cubetutor.domain.Handle

/**
 * What a hit on the Stage can be (design entities3d): a sticker (reading / fixing colours), a layer handle, one of the
 * bar's two arrows, the tray (drag the rig) or nothing of ours. Entity names come only from [CubeNames] and are parsed
 * back only by [resolveName] (board-input README: one pure pair, replayed in unit tests with resolvePath).
 */
sealed class CubeTarget {
    data class Sticker(val index: Int) : CubeTarget()
    data class HandleT(val handle: Handle) : CubeTarget()
    data class Arrow(val first: Boolean) : CubeTarget()
    object Tray : CubeTarget()
    object Chip : CubeTarget()

    override fun toString(): String =
        when (this) {
            is Sticker -> "Sticker(${Facelets.name(index)})"
            is HandleT -> "Handle(${handle.id})"
            is Arrow -> "Arrow(${if (first) "a" else "b"})"
            Tray -> "Tray"
            Chip -> "Chip"
        }
}

object CubeNames {
    fun sticker(i: Int) = "st:${Facelets.name(i)}"

    fun handle(h: Handle) = "h:${h.id}"

    fun arrow(first: Boolean) = "dp:${if (first) "a" else "b"}"

    const val TRAY = "tray"

    /** Visible label / plate children carry a prefix and resolve through their parent (resolveUp walks up). */
    fun label(of: String) = "lbl:$of"

    fun resolveName(name: String): CubeTarget? =
        when {
            name.startsWith("st:") -> Facelets.parse(name.removePrefix("st:"))?.let { CubeTarget.Sticker(it) }
            name.startsWith("h:") -> Handle.parse(name.removePrefix("h:"))?.let { CubeTarget.HandleT(it) }
            name == "dp:a" -> CubeTarget.Arrow(true)
            name == "dp:b" -> CubeTarget.Arrow(false)
            name == TRAY -> CubeTarget.Tray
            else -> null
        }
}
