package eu.darken.sdmse.common.files

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
enum class FileType : Parcelable {
    DIRECTORY, SYMBOLIC_LINK, FILE, UNKNOWN
}