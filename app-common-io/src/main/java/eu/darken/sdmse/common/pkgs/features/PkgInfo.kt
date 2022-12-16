package eu.darken.sdmse.common.pkgs.features

import android.content.pm.PackageInfo
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId

interface PkgInfo : Pkg {
    val packageInfo: PackageInfo

    override val id: Pkg.Id
        get() = packageInfo.packageName.toPkgId()

    fun <T> tryField(fieldName: String): T? {
        val field = PackageInfo::class.java.getDeclaredField(fieldName).apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        return field.get(packageInfo) as? T
    }
}