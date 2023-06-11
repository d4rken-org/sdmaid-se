package eu.darken.sdmse.exclusion.ui.list

import eu.darken.sdmse.exclusion.core.types.Exclusion

sealed class ExclusionListEvents {
    data class UndoRemove(val exclusions: Set<Exclusion>) : ExclusionListEvents()
}
