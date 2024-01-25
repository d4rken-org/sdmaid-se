package eu.darken.sdmse.common.previews.item

import android.os.Parcelable
import eu.darken.sdmse.common.files.APath
import kotlinx.parcelize.Parcelize

@Parcelize
data class PreviewItem(
    val path: APath,
) : Parcelable
