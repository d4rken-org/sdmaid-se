package eu.darken.sdmse.common.uix

import java.lang.Integer.min

fun <T, ID> resolveTarget(
    items: List<T>,
    requestedTarget: ID?,
    lastPosition: Int?,
    identifierOf: (T) -> ID,
    onPositionTracked: (Int) -> Unit,
): ID? {
    val currentIndex = items.indexOfFirst { identifierOf(it) == requestedTarget }
    if (currentIndex != -1) onPositionTracked(currentIndex)

    return when {
        items.isEmpty() -> null
        currentIndex != -1 -> requestedTarget
        lastPosition != null -> identifierOf(items[min(lastPosition, items.size - 1)])
        else -> requestedTarget
    }
}
