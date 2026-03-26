@file:UseSerializers(APathSerializer::class)

package eu.darken.sdmse.common.picker

import android.os.Parcelable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.serialization.APathSerializer
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
@Parcelize
data class PickerRequest(
    val requestKey: String,
    val mode: PickMode,
    val allowedAreas: Set<DataArea.Type> = emptySet(),
    val selectedPaths: List<APath> = emptyList(),
) : Parcelable {
    @Serializable
    enum class PickMode {
        DIR, DIRS
    }
}
