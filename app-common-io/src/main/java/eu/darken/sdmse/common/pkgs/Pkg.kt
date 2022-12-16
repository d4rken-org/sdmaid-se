package eu.darken.sdmse.common.pkgs

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

interface Pkg {

    val id: Id

    val packageName: String
        get() = id.name

    fun getLabel(context: Context): String? = null

    fun getIcon(context: Context): Drawable? = null

    @Parcelize
    data class Id(
        val name: String,
    ) : Parcelable {
        override fun toString(): String = name
    }

}
