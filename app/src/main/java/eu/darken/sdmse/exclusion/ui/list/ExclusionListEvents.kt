package eu.darken.sdmse.exclusion.ui.list

import android.content.Intent
import eu.darken.sdmse.exclusion.core.types.Exclusion

sealed class ExclusionListEvents {
    data class UndoRemove(val exclusions: Set<Exclusion>) : ExclusionListEvents()
    data class ImportEvent(val intent: Intent) : ExclusionListEvents()
    data class ExportEvent(val intent: Intent, val filter: Collection<Exclusion>) : ExclusionListEvents()
}
