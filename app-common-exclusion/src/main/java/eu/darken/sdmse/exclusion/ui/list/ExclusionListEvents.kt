package eu.darken.sdmse.exclusion.ui.list

import android.content.Intent
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId

sealed class ExclusionListEvents {
    data class UndoRemove(val exclusions: Set<Exclusion>) : ExclusionListEvents()
    data class ImportSuccess(val exclusions: Set<Exclusion>) : ExclusionListEvents()
    data class ExportSuccess(val exclusions: Set<Exclusion>) : ExclusionListEvents()
    data class ImportEvent(val intent: Intent) : ExclusionListEvents()
    data class ExportEvent(val intent: Intent, val ids: Collection<ExclusionId>) : ExclusionListEvents()
}
