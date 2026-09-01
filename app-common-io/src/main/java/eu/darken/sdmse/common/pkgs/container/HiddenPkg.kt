package eu.darken.sdmse.common.pkgs.container

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.cache
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.loadIconFromArchive
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserHandle2

data class HiddenPkg(
    override val packageInfo: PackageInfo,
    override val userHandle: UserHandle2,
    override val installerInfo: InstallerInfo = InstallerInfo(),
    val apkPath: APath? = null,
) : Installed, InstallDetails {

    override val id: Pkg.Id = packageInfo.packageName.toPkgId()

    override val isSystemApp: Boolean
        get() {
            val fromFlags = applicationInfo?.run { flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            if (fromFlags == true) return true
            // getPackageArchiveInfo() does not populate FLAG_SYSTEM (same limitation LibraryPkg
            // documents), so for an archive-parsed instance the partition is the only signal.
            apkPath?.let { path -> return SYSTEM_PARTITIONS.any { path.path.startsWith(it) } }
            return fromFlags ?: true
        }

    override val label: CaString = caString { context ->
        context.packageManager.getLabel2(id) ?: id.name
    }.cache()

    override val icon: ((Context) -> Drawable)? = { context ->
        packageInfo.loadIconFromArchive(context.packageManager)
            ?: AppCompatResources.getDrawable(context, R.drawable.ic_default_app_icon_24)!!
    }

    override fun toString(): String = "HiddenPkg(packageName=$packageName, userHandle=$userHandle)"

    companion object {
        private val SYSTEM_PARTITIONS = listOf(
            "/system/",
            "/system_ext/",
            "/product/",
            "/vendor/",
            "/odm/",
            "/apex/",
        )
    }
}
