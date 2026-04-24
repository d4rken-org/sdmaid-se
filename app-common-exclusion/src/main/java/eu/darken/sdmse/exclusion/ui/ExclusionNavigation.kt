package eu.darken.sdmse.exclusion.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.exclusion.ui.editor.pkg.PkgExclusionEditorScreenHost
import eu.darken.sdmse.exclusion.ui.editor.segment.SegmentExclusionEditorScreenHost
import javax.inject.Inject

class ExclusionNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<PkgExclusionEditorRoute> { route -> PkgExclusionEditorScreenHost(route = route) }
        entry<SegmentExclusionEditorRoute> { route -> SegmentExclusionEditorScreenHost(route = route) }
    }
}
