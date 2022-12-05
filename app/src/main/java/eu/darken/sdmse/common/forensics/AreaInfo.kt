package eu.darken.sdmse.common.forensics

import android.os.Parcelable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.APath
import kotlinx.parcelize.Parcelize

@Parcelize
data class AreaInfo(
    val dataArea: DataArea,
    val file: APath,
    val prefix: String,
    val isBlackListLocation: Boolean,
) : Parcelable {

    val type: DataArea.Type
        get() = dataArea.type

    val prefixFreePath: String
        get() = file.path.replace(prefix, "")

}