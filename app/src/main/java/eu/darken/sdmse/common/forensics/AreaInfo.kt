package eu.darken.sdmse.common.forensics

import android.os.Parcelable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.Segments
import kotlinx.parcelize.Parcelize

@Parcelize
data class AreaInfo(
    val dataArea: DataArea,
    val file: APath,
    val prefix: APath,
    val isBlackListLocation: Boolean,
) : Parcelable {

    val type: DataArea.Type
        get() = dataArea.type

    // TODO would subList have noticeably better performance?
    val prefixFreePath: Segments
        get() = file.segments.drop(prefix.segments.size)

}