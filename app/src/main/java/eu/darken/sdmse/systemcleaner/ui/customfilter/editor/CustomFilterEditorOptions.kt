package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import android.os.Parcelable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import kotlinx.parcelize.Parcelize

@Parcelize
data class CustomFilterEditorOptions(
    val areas: Set<DataArea.Type>? = null,
    val label: String? = null,
    val pathCriteria: Set<SegmentCriterium>? = null,
    val nameCriteria: Set<NameCriterium>? = null,
    val saveAsEnabled: Boolean = false,
) : Parcelable