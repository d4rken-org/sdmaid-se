package eu.darken.sdmse.common.pkgs

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import eu.darken.sdmse.common.ca.CaString
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

interface Pkg {

    val id: Id

    val packageName: String
        get() = id.name

    val label: CaString?

    val icon: ((Context) -> Drawable)?

    @Serializable
    @Parcelize
    data class Id(
        val name: String,
    ) : Parcelable {
        override fun toString(): String = name
    }

}
