package eu.darken.sdmse.setup.automation

import android.content.Context
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.features.getInstallerInfo
import eu.darken.sdmse.common.pkgs.toPkgId

private val SANCTIONED_SOURCES = setOf(
    "com.android.vending".toPkgId(),
    "org.fdroid.fdroid".toPkgId(),
)
private val FLAGGED_SOURCE_TYPES = setOf(
    InstallerInfo.SourceType.LOCAL_FILE,
    InstallerInfo.SourceType.DOWNLOADED_FILE,
    InstallerInfo.SourceType.UNSPECIFIED,
)

fun Context.mightBeRestrictedDueToSideload(): Boolean {
    if (!hasApiLevel(33)) return false

    val installerInfo = packageManager
        .getPackageInfo(packageName, 0)
        .getInstallerInfo(packageManager)

    val hasGoodSource = installerInfo.allInstallers.any { SANCTIONED_SOURCES.contains(it.id) }
    if (hasGoodSource) return false

    // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/pm/InstallPackageHelper.java;l=2180
    return FLAGGED_SOURCE_TYPES.contains(installerInfo.sourceType)
}