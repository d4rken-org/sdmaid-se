package eu.darken.sdmse.common.pkgs.container

import android.annotation.SuppressLint
import android.content.pm.ArchivedPackageInfo
import android.content.pm.PackageInfo
import androidx.appcompat.content.res.AppCompatResources
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caDrawable
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.cache
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.getIcon2
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserHandle2

@SuppressLint("NewApi")
data class ArchivedPkg(
    override val packageInfo: PackageInfo,
    override val userHandle: UserHandle2,
    override val installerInfo: InstallerInfo,
    private val archivedPackageInfo: ArchivedPackageInfo? = null,
) : Installed, InstallDetails {

    override val id: Pkg.Id
        get() = packageInfo.packageName.toPkgId()

    override val label: CaString = caString { context ->
        // Try to get label from ArchivedPackageInfo first (works for archived apps)
        archivedPackageInfo?.launcherActivities?.firstOrNull()?.label?.toString()
            ?: context.packageManager.getLabel2(id) // Fall back to standard PM API (may not work for archived apps)
            ?: id.name
    }.cache()

    override val icon: CaDrawable = caDrawable { context ->
        context.packageManager.getIcon2(id) ?: AppCompatResources.getDrawable(context, R.drawable.ic_archive_24)!!
    }.cache()

    override val isEnabled: Boolean
        get() = false

    override fun toString(): String = "ArchivedPkg(packageName=$packageName)"
}