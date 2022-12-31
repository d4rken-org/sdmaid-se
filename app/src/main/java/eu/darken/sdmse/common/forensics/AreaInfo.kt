package eu.darken.sdmse.common.forensics

import android.os.Parcelable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.APath
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
    val prefixFreePath: List<String>
        get() = file.segments.drop(prefix.segments.size)

}