package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import android.os.Parcelable
import eu.darken.sdmse.common.areas.DataArea
import kotlinx.parcelize.Parcelize

@Parcelize
data class CustomFilterEditorOptions(
    val areas: Set<DataArea.Type>? = null,
) : Parcelable