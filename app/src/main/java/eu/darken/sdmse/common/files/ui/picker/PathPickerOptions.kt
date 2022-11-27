package eu.darken.sdmse.common.files.ui.picker

import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.Keep
import eu.darken.sdmse.common.files.core.APath
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Keep @Parcelize
data class PathPickerOptions(
    val startPath: eu.darken.sdmse.common.files.core.APath? = null,
    val selectionLimit: Int = 1,
    val allowedTypes: Set<eu.darken.sdmse.common.files.core.APath.PathType> = emptySet(),
    val onlyDirs: Boolean = true,
    val allowCreateDir: Boolean = true,
    val payload: Bundle = Bundle()
) : Parcelable {
    @IgnoredOnParcel @Transient val type: eu.darken.sdmse.common.files.core.APath.PathType? = startPath?.pathType

}