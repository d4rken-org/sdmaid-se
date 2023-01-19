package eu.darken.sdmse.common.forensics

import android.os.Parcelable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.Segments
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class AreaInfo(
    val file: APath,
    val prefix: APath,
    val dataArea: DataArea,
    val isBlackListLocation: Boolean,
) : Parcelable {

    val type: DataArea.Type
        get() = dataArea.type

    // TODO would subList have noticeably better performance?
    @IgnoredOnParcel internal var prefixCache: Segments? = null

    val prefixFreePath: Segments
        //        get() = file.segments.drop(prefix.segments.size)
        get() = prefixCache
            ?: run {
                file.segments.subList(prefix.segments.size, file.segments.size)
            }.also { prefixCache = it }

}