package eu.darken.sdmse.common.pkgs.features

import android.content.pm.PackageInfo
import eu.darken.sdmse.common.pkgs.Pkg

interface HasPackageInfo : Pkg {
    val packageInfo: PackageInfo


    fun <T> tryField(fieldName: String): T? {
        val field = PackageInfo::class.java.getDeclaredField(fieldName).apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        return field.get(packageInfo) as? T
    }
}