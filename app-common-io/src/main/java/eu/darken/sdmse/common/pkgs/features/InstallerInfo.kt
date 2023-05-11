package eu.darken.sdmse.common.pkgs.features

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
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
    val sourceType: SourceType = SourceType.UNSPECIFIED,
) {

    val allInstallers: List<Pkg>
        get() = listOfNotNull(installingPkg, initiatingPkg, originatingPkg)

    val installer: Pkg?
        get() = allInstallers.firstOrNull()

    fun getLabel(context: Context): String {
        if (installer == null) {
            return context.getString(eu.darken.sdmse.common.R.string.general_na_label)
        }

        return installingPkg?.label?.get(context) ?: installer!!.id.name
    }

    fun getIcon(context: Context): Drawable {
        if (installer == null) return ContextCompat.getDrawable(context, R.drawable.ic_baseline_user_24)!!

        installer!!.icon?.get(context)?.let { return it }

        return ContextCompat.getDrawable(context, R.drawable.ic_default_app_icon_24)!!
    }

    enum class SourceType {
        UNSPECIFIED,
        STORE,
        LOCAL_FILE,
        DOWNLOADED_FILE,
        OTHER,
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

    val sourceType = if (hasApiLevel(33)) {
        @SuppressLint("NewApi")
        when (sourceInfo?.packageSource) {
            PackageInstaller.PACKAGE_SOURCE_OTHER -> InstallerInfo.SourceType.OTHER
            PackageInstaller.PACKAGE_SOURCE_STORE -> InstallerInfo.SourceType.STORE
            PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE -> InstallerInfo.SourceType.LOCAL_FILE
            PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE -> InstallerInfo.SourceType.DOWNLOADED_FILE
            else -> InstallerInfo.SourceType.UNSPECIFIED
        }
    } else {
        InstallerInfo.SourceType.UNSPECIFIED
    }

    return InstallerInfo(
        initiatingPkg = initiatingPkg,
        installingPkg = installingPkg,
        originatingPkg = originatingPkg,
        sourceType = sourceType
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