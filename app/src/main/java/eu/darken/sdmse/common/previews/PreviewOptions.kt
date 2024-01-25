package eu.darken.sdmse.common.previews

import android.os.Parcelable
import eu.darken.sdmse.common.files.APath
import kotlinx.parcelize.Parcelize

@Parcelize
data class PreviewOptions(
    val paths: List<APath>,
    val position: Int = 0,
) : Parcelable {

    constructor(path: APath) : this(paths = listOf(path))

}
