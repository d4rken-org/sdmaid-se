package eu.darken.sdmse.common.pkgs.container

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import eu.darken.sdmse.common.pkgs.AKnownPkg
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.ReadableApk
import eu.darken.sdmse.common.pkgs.getIcon2
import eu.darken.sdmse.common.pkgs.getLabel2

data class ApkInfo(
    override val id: Pkg.Id,
    override val packageInfo: PackageInfo
) : Pkg, ReadableApk {
    override fun getLabel(context: Context): String? {
        context.packageManager.getLabel2(id)?.let { return it }

        AKnownPkg.values
            .singleOrNull { it.id == id }
            ?.labelRes
            ?.let { return context.getString(it) }

        return null
    }

    override fun getIcon(context: Context): Drawable? {
        context.packageManager.getIcon2(id)?.let { return it }

        AKnownPkg.values
            .singleOrNull { it.id == id }
            ?.iconRes
            ?.let { ContextCompat.getDrawable(context, it) }
            ?.let { return it }

        return null
    }
}