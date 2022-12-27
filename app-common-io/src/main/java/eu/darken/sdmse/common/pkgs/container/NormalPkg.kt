package eu.darken.sdmse.common.pkgs.container

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
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
    override val userHandles: Set<UserHandle2>,
) : Installed, ReadableApk, ExtendedInstallData {

    private var _label: String? = null
    override fun getLabel(context: Context): String {
        _label?.let { return it }
        val newLabel = context.packageManager.getLabel2(id)
            ?: id.name
        _label = newLabel
        return newLabel
    }

    override fun getIcon(context: Context): Drawable =
        context.packageManager.getIcon2(id)
            ?: AppCompatResources.getDrawable(context, R.drawable.ic_default_app_icon_24)!!

    override fun toString(): String = "NormalPkg(packageName=$packageName, userHandles=$userHandles)"
}