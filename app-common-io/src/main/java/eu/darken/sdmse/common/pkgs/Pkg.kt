package eu.darken.sdmse.common.pkgs

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import kotlinx.parcelize.Parcelize

interface Pkg {

    val id: Id

    val packageName: String
        get() = id.name

    val label: CaString?

    val icon: CaDrawable?

    @Parcelize
    @JsonClass(generateAdapter = true)
    data class Id(
        @Json(name = "name") val name: String,
    ) : Parcelable {
        override fun toString(): String = name
    }

}
