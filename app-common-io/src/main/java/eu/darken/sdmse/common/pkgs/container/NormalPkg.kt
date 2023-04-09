package eu.darken.sdmse.common.pkgs.container

import android.content.pm.PackageInfo
import androidx.appcompat.content.res.AppCompatResources
import eu.darken.sdmse.common.ca.*
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.pkgs.features.ExtendedInstallData
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.features.ReadableApk
import eu.darken.sdmse.common.pkgs.getIcon2
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.user.UserHandle2

data class NormalPkg(
    override val packageInfo: PackageInfo,
    override val installerInfo: InstallerInfo,
    override val userHandle: UserHandle2,
) : Installed, ReadableApk, ExtendedInstallData {

    override val label: CaString = caString { context ->
        context.packageManager.getLabel2(id)
            ?: id.name
    }.cache()

    override val icon: CaDrawable = caDrawable { context ->
        context.packageManager.getIcon2(id)
            ?: AppCompatResources.getDrawable(context, R.drawable.ic_default_app_icon_24)!!
    }.cache()

    override fun toString(): String = "NormalPkg(packageName=$packageName, userHandle=$userHandle)"
}