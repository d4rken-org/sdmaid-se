@file:UseSerializers(APathSerializer::class)

package eu.darken.sdmse.common.previews

import android.os.Parcelable
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.serialization.APathSerializer
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
@Parcelize
data class PreviewOptions(
    val paths: List<APath>,
    val position: Int = 0,
) : Parcelable {

    constructor(path: APath) : this(paths = listOf(path))

}
