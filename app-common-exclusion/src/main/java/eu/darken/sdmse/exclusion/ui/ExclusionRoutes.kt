package eu.darken.sdmse.exclusion.ui

import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionEditorOptions
import eu.darken.sdmse.exclusion.ui.editor.pkg.PkgExclusionEditorOptions
import eu.darken.sdmse.exclusion.ui.editor.segment.SegmentExclusionEditorOptions
import kotlinx.serialization.Serializable

@Serializable
data object ExclusionsListRoute : NavigationDestination

@Serializable
data class PathExclusionEditorRoute(
    val exclusionId: String? = null,
    val initial: PathExclusionEditorOptions? = null,
) : NavigationDestination

@Serializable
data class PkgExclusionEditorRoute(
    val exclusionId: String? = null,
    val initial: PkgExclusionEditorOptions? = null,
) : NavigationDestination

@Serializable
data class SegmentExclusionEditorRoute(
    val exclusionId: String? = null,
    val initial: SegmentExclusionEditorOptions? = null,
) : NavigationDestination
