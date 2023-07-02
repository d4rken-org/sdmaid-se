package eu.darken.sdmse.common.pkgs.features

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId

interface PkgInfo : Pkg {
    val packageInfo: PackageInfo

    val applicationInfo: ApplicationInfo?
        get() = packageInfo.applicationInfo

    override val id: Pkg.Id
        get() = packageInfo.packageName.toPkgId()

    val versionCode: Long
        get() = PackageInfoCompat.getLongVersionCode(packageInfo)

    val versionName: String?
        get() = packageInfo.versionName

    fun <T> tryField(fieldName: String): T? {
        val field = PackageInfo::class.java.getDeclaredField(fieldName).apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        return field.get(packageInfo) as? T
    }
}