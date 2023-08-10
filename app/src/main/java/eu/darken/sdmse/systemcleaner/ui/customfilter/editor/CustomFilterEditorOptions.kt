package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import android.os.Parcelable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import kotlinx.parcelize.Parcelize

@Parcelize
data class CustomFilterEditorOptions(
    val areas: Set<DataArea.Type>? = null,
    val label: String? = null,
    val pathCriteria: Set<CustomFilterConfig.SegmentCriterium>? = null,
    val nameCriteria: Set<CustomFilterConfig.NameCriterium>? = null,
) : Parcelable