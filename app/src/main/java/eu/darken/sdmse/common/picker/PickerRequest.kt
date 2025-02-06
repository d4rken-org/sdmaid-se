package eu.darken.sdmse.common.picker

import android.os.Parcelable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.APath
import kotlinx.parcelize.Parcelize

@Parcelize
data class PickerRequest(
    val mode: PickMode,
    val allowedAreas: Set<DataArea.Type> = emptySet(),
    val selectedPaths: List<APath> = emptyList(),
) : Parcelable {
    enum class PickMode {
        MIXED, FILE, FILES, DIR, DIRS
    }
}
