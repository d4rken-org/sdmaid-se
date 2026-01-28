package eu.darken.sdmse.automation.core.uidumper

import android.graphics.Rect

data class UiNode(
    val text: String?,
    val contentDesc: String?,
    val resourceId: String?,
    val className: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEnabled: Boolean,
    val children: List<UiNode>,
) {
    val centerX: Int get() = bounds.centerX()
    val centerY: Int get() = bounds.centerY()

    fun flatten(): List<UiNode> = listOf(this) + children.flatMap { it.flatten() }
}
