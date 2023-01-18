package eu.darken.sdmse.common.pkgs.container

import android.content.pm.PackageInfo
import androidx.core.content.ContextCompat
import eu.darken.sdmse.common.ca.*
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.pkgs.AKnownPkg
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.ReadableApk
import eu.darken.sdmse.common.pkgs.getIcon2
import eu.darken.sdmse.common.pkgs.getLabel2

data class ApkInfo(
    override val id: Pkg.Id,
    override val packageInfo: PackageInfo
) : Pkg, ReadableApk {

    override val label: CaString = caString { context ->
        context.packageManager.getLabel2(id)?.let { return@caString it }

        AKnownPkg.values
            .singleOrNull { it.id == id }
            ?.labelRes
            ?.let { return@caString context.getString(it) }

        id.name
    }.cache()

    override val icon: CaDrawable = caDrawable { context ->
        context.packageManager.getIcon2(id)?.let { return@caDrawable it }

        AKnownPkg.values
            .singleOrNull { it.id == id }
            ?.iconRes
            ?.let { ContextCompat.getDrawable(context, it) }
            ?.let { return@caDrawable it }

        ContextCompat.getDrawable(context, R.drawable.ic_default_app_icon_24)!!
    }.cache()
}