package eu.darken.sdmse.common.picker

import android.os.Parcelable
import eu.darken.sdmse.common.files.APath
import kotlinx.parcelize.Parcelize

@Parcelize
data class PickerResult(
    val selectedPaths: Set<APath>,
) : Parcelable
