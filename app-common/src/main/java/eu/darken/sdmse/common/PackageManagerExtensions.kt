package eu.darken.sdmse.common

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

fun PackageManager.getPackageInfo2(
    packageName: String,
    flags: Int = 0
): PackageInfo? = try {
    getPackageInfo(packageName, flags)
} catch (_: PackageManager.NameNotFoundException) {
    null
}

fun PackageManager.getLabel2(
    pkgId: String,
): String? = getPackageInfo2(pkgId)
    ?.applicationInfo
    ?.let {
        if (it.labelRes != 0) it.loadLabel(this).toString()
        else it.nonLocalizedLabel?.toString()
    }

fun PackageManager.getIcon2(
    pkgId: String,
): Drawable? = getPackageInfo2(pkgId)
    ?.applicationInfo
    ?.let { if (it.icon != 0) it.loadIcon(this) else null }

val PackageInfo.isSystemApp: Boolean
    get() = applicationInfo?.run { flags and ApplicationInfo.FLAG_SYSTEM != 0 } ?: true