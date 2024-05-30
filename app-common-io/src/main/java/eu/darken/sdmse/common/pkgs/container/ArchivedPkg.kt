package eu.darken.sdmse.common.pkgs.container

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import androidx.appcompat.content.res.AppCompatResources
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caDrawable
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.cache
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.ExtendedInstallData
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.getIcon2
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserHandle2

data class ArchivedPkg(
    override val packageInfo: PackageInfo,
    override val userHandle: UserHandle2,
    override val installerInfo: InstallerInfo,
) : Installed, ExtendedInstallData {

    override val id: Pkg.Id
        get() = packageInfo.packageName.toPkgId()

    @get:SuppressLint("NewApi")
    override val versionCode: Long
        get() = -1L

    override val label: CaString = caString { context ->
        context.packageManager.getLabel2(id) ?: id.name
    }.cache()

    override val icon: CaDrawable = caDrawable { context ->
        context.packageManager.getIcon2(id) ?: AppCompatResources.getDrawable(context, R.drawable.baseline_archive_24)!!
    }.cache()

    override val isEnabled: Boolean
        get() = false


    override fun toString(): String = "ArchivedPkg(packageName=$packageName)"
}