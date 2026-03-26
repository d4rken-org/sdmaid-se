package eu.darken.sdmse.exclusion.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.serializableNavType
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionEditorOptions
import eu.darken.sdmse.exclusion.ui.editor.pkg.PkgExclusionEditorOptions
import eu.darken.sdmse.exclusion.ui.editor.segment.SegmentExclusionEditorOptions
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data object ExclusionsListRoute

@Serializable
data class PathExclusionEditorRoute(
    val exclusionId: String? = null,
    val initial: PathExclusionEditorOptions? = null,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<PathExclusionEditorOptions?>() to serializableNavType(PathExclusionEditorOptions.serializer(), isNullableAllowed = true),
        )

        fun from(handle: SavedStateHandle) = handle.toRoute<PathExclusionEditorRoute>(typeMap)
    }
}

@Serializable
data class PkgExclusionEditorRoute(
    val exclusionId: String? = null,
    val initial: PkgExclusionEditorOptions? = null,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<PkgExclusionEditorOptions?>() to serializableNavType(PkgExclusionEditorOptions.serializer(), isNullableAllowed = true),
        )

        fun from(handle: SavedStateHandle) = handle.toRoute<PkgExclusionEditorRoute>(typeMap)
    }
}

@Serializable
data class SegmentExclusionEditorRoute(
    val exclusionId: String? = null,
    val initial: SegmentExclusionEditorOptions? = null,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<SegmentExclusionEditorOptions?>() to serializableNavType(SegmentExclusionEditorOptions.serializer(), isNullableAllowed = true),
        )

        fun from(handle: SavedStateHandle) = handle.toRoute<SegmentExclusionEditorRoute>(typeMap)
    }
}
