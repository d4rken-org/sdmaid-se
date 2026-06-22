package eu.darken.sdmse.common.pkgs.container

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.cache
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.pkgs.features.ApiDetails
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.features.PermissionDetails
import eu.darken.sdmse.common.pkgs.features.SourceAvailable
import eu.darken.sdmse.common.pkgs.getIcon2
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.user.UserHandle2

data class NormalPkg(
    override val packageInfo: PackageInfo,
    override val installerInfo: InstallerInfo,
    override val userHandle: UserHandle2,
) : Installed, InstallDetails, SourceAvailable, PermissionDetails, ApiDetails {

    override val label: CaString = caString { context ->
        context.packageManager.getLabel2(id)
            ?: id.name
    }.cache()

    override val icon: ((Context) -> Drawable)? = { context ->
        context.packageManager.getIcon2(id)
            ?: AppCompatResources.getDrawable(context, R.drawable.ic_default_app_icon_24)!!
    }

    override fun toString(): String = "NormalPkg(packageName=$packageName, userHandle=$userHandle)"
}