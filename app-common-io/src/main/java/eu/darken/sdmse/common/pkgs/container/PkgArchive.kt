package eu.darken.sdmse.common.pkgs.container

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.cache
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.pkgs.AKnownPkg
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.PermissionDetails
import eu.darken.sdmse.common.pkgs.features.PkgInfo
import eu.darken.sdmse.common.pkgs.getIcon2
import eu.darken.sdmse.common.pkgs.getLabel2

data class PkgArchive(
    override val id: Pkg.Id,
    override val packageInfo: PackageInfo
) : PkgInfo, PermissionDetails {

    override val label: CaString = caString { context ->
        context.packageManager.getLabel2(id)?.let { return@caString it }

        AKnownPkg.values
            .singleOrNull { it.id == id }
            ?.labelRes
            ?.let { return@caString context.getString(it) }

        id.name
    }.cache()

    override val icon: ((Context) -> Drawable)? = { context ->
        context.packageManager.getIcon2(id)
            ?: AKnownPkg.values.singleOrNull { it.id == id }?.icon?.invoke(context)
            ?: ContextCompat.getDrawable(context, R.drawable.ic_default_app_icon_24)!!
    }
}
