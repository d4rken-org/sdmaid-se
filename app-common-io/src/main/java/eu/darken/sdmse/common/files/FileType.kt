package eu.darken.sdmse.common.files

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Keep
@Parcelize
enum class FileType : Parcelable {
    @SerialName("DIRECTORY") DIRECTORY,
    @SerialName("SYMBOLIC_LINK") SYMBOLIC_LINK,
    @SerialName("FILE") FILE,
    @SerialName("UNKNOWN") UNKNOWN,
}