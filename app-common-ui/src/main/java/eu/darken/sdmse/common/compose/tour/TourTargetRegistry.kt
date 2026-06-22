package eu.darken.sdmse.common.compose.tour

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

@Stable
class TourTargetRegistry {

    private data class Entry(val rect: Rect, val owner: Any)

    private val entries = mutableStateMapOf<String, Entry>()

    fun put(id: String, rect: Rect, owner: Any) {
        entries[id] = Entry(rect, owner)
    }

    fun removeIfOwner(id: String, owner: Any) {
        if (entries[id]?.owner === owner) entries.remove(id)
    }

    fun get(id: String): Rect? = entries[id]?.rect

    fun has(id: String): Boolean = entries.containsKey(id)
}

val LocalTourTargetRegistry: ProvidableCompositionLocal<TourTargetRegistry> =
    staticCompositionLocalOf { TourTargetRegistry() }

fun Modifier.guidedTourTarget(id: String): Modifier = composed {
    val registry = LocalTourTargetRegistry.current
    val ownerToken = remember { Any() }
    DisposableEffect(id, registry, ownerToken) {
        onDispose { registry.removeIfOwner(id, ownerToken) }
    }
    onGloballyPositioned { coords ->
        val rect = coords.boundsInRoot()
        if (!rect.isFinite || rect.width <= 0f || rect.height <= 0f) return@onGloballyPositioned
        if (rect != registry.get(id)) registry.put(id, rect, ownerToken)
    }
}
