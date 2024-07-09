package eu.darken.sdmse.systemcleaner.ui.customfilter.list

import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.RawFilter

sealed class CustomFilterListEvents {
    data class UndoRemove(val exclusions: Set<CustomFilterConfig>) : CustomFilterListEvents()
    data class ImportEvent(val intent: Intent) : CustomFilterListEvents()
    data class ExportEvent(val intent: Intent, val filter: Collection<RawFilter>) : CustomFilterListEvents()
    data class ExportFinished(val path: DocumentFile, val files: List<DocumentFile>) : CustomFilterListEvents()
}
