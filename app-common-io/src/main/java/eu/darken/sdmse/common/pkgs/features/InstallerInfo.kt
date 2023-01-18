package eu.darken.sdmse.common.pkgs.features

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.pkgs.AKnownPkg
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.container.toStub
import eu.darken.sdmse.common.pkgs.toKnownPkg
import eu.darken.sdmse.common.pkgs.toPkgId

data class InstallerInfo(
    val installingPkg: Pkg?,
    val initiatingPkg: Pkg? = null,
    val originatingPkg: Pkg? = null,
) {

    val allInstallers: List<Pkg>
        get() = listOfNotNull(installingPkg, initiatingPkg, originatingPkg)

    val installer: Pkg?
        get() = allInstallers.firstOrNull()

    fun getLabel(context: Context): String {
        if (installer == null) {
            return context.getString(R.string.general_na_label)
        }

        return installingPkg?.label?.get(context) ?: installer!!.id.name
    }

    fun getIcon(context: Context): Drawable {
        if (installer == null) return ContextCompat.getDrawable(context, R.drawable.ic_baseline_user_24)!!

        installer!!.icon?.get(context)?.let { return it }

        return ContextCompat.getDrawable(context, R.drawable.ic_default_app_icon_24)!!
    }
}

fun ExtendedInstallData.isSideloaded(): Boolean {
    if (isSystemApp) return false
    return installerInfo.allInstallers.none { it.id == AKnownPkg.GooglePlay.id }
}

@SuppressLint("NewApi")
fun PackageInfo.getInstallerInfo(
    packageManager: PackageManager,
): InstallerInfo = if (hasApiLevel(Build.VERSION_CODES.R)) {
    getInstallerInfoApi30(packageManager)
} else {
    getInstallerInfoLegacy(packageManager)
}

@RequiresApi(Build.VERSION_CODES.R)
private fun PackageInfo.getInstallerInfoApi30(packageManager: PackageManager): InstallerInfo {
    val sourceInfo = try {
        packageManager.getInstallSourceInfo(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
    val initiatingPkg = sourceInfo?.initiatingPackageName
        ?.toPkgId()
        ?.let { it.toKnownPkg() ?: it.toStub() }

    val installingPkg = sourceInfo?.installingPackageName
        ?.toPkgId()
        ?.let { it.toKnownPkg() ?: it.toStub() }

    val originatingPkg = sourceInfo?.originatingPackageName
        ?.toPkgId()
        ?.let { it.toKnownPkg() ?: it.toStub() }

    return InstallerInfo(
        initiatingPkg = initiatingPkg,
        installingPkg = installingPkg,
        originatingPkg = originatingPkg,
    )
}

private fun PackageInfo.getInstallerInfoLegacy(packageManager: PackageManager): InstallerInfo {
    val installingPkg = try {
        packageManager.getInstallerPackageName(packageName)
            ?.let { Pkg.Id(it) }
            ?.let { it.toKnownPkg() ?: it.toStub() }
    } catch (e: IllegalArgumentException) {
        log(WARN) { "OS race condition, package ($packageName) was uninstalled?: ${e.asLog()}" }
        null
    }

    return InstallerInfo(
        installingPkg = installingPkg,
    )
}