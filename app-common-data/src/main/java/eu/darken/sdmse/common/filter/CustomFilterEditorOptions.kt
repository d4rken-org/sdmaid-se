package eu.darken.sdmse.common.filter

import android.os.Parcelable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class CustomFilterEditorOptions(
    val areas: Set<DataArea.Type>? = null,
    val label: String? = null,
    val pathCriteria: Set<SegmentCriterium>? = null,
    val nameCriteria: Set<NameCriterium>? = null,
    val saveAsEnabled: Boolean = false,
) : Parcelable