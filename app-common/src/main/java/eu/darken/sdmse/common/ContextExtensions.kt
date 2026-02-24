package eu.darken.sdmse.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.annotation.PluralsRes
import okio.Source
import okio.source


@SuppressLint("NewApi")
fun Context.startServiceCompat(intent: Intent) {
    if (hasApiLevel(26)) startForegroundService(intent) else startService(intent)
}

fun Context.getQuantityString2(
    @PluralsRes stringRes: Int,
    quantity: Int
) = resources.getQuantityString(stringRes, quantity, quantity)

fun Context.getQuantityString2(
    @PluralsRes stringRes: Int,
    quantity: Int,
    vararg formatArgs: Any
) = resources.getQuantityString(stringRes, quantity, *formatArgs)

fun Context.openAsset(path: String): Source {
    return assets.open(path).source()
}

fun Context.isInstalled(pkgName: String) = try {
    @Suppress("DEPRECATION")
    this.packageManager.getPackageInfo(pkgName, 0) != null
} catch (_: PackageManager.NameNotFoundException) {
    false
}

fun Context.getPackageInfo(): PackageInfo = packageManager.getPackageInfo(packageName, 0)
