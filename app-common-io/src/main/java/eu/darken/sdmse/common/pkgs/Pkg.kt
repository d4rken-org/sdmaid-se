package eu.darken.sdmse.common.pkgs

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import androidx.core.content.ContextCompat
import kotlinx.parcelize.Parcelize

interface Pkg {

    val id: Id

    val packageName: String
        get() = id.name

    fun getLabel(context: Context): String? {
        context.packageManager.getLabel2(id)?.let { return it }

        AKnownPkg.values
            .singleOrNull { it.id == id }
            ?.labelRes
            ?.let { return context.getString(it) }

        return null
    }

    fun getIcon(context: Context): Drawable? {
        context.packageManager.getIcon2(id)?.let { return it }

        AKnownPkg.values
            .singleOrNull { it.id == id }
            ?.iconRes
            ?.let { ContextCompat.getDrawable(context, it) }
            ?.let { return it }

        return null
    }

    @Parcelize
    data class Id(
        val name: String,
    ) : Parcelable {
        override fun toString(): String = name
    }

}
