package eu.darken.sdmse.appcontrol.ui.preview

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.AppControlListViewModel
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.SourceAvailable
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.user.UserHandle2
import java.time.Instant

/**
 * Fake [Installed] for `@Preview2` rendering only. Backed by a real [PackageInfo] + [ApplicationInfo]
 * so the [InstallDetails] / [SourceAvailable] defaults (which read app-info flags) resolve to a
 * normal, enabled, user-installed app instead of the "system / no source" fallbacks. No
 * PackageManager calls happen, so this is IDE-preview safe.
 */
private class PreviewInstalled(
    private val pkgName: String,
    private val displayLabel: String,
    versionName: String,
    versionCodeValue: Long,
    installedAt: Instant,
    updatedAt: Instant,
    sourceDirPath: String?,
    override val userHandle: UserHandle2 = UserHandle2(handleId = 0),
) : Installed, InstallDetails, SourceAvailable {

    override val packageInfo: PackageInfo = PackageInfo().apply {
        packageName = pkgName
        this.versionName = versionName
        @Suppress("DEPRECATION")
        versionCode = versionCodeValue.toInt()
        longVersionCode = versionCodeValue
        firstInstallTime = installedAt.toEpochMilli()
        lastUpdateTime = updatedAt.toEpochMilli()
        applicationInfo = ApplicationInfo().apply {
            packageName = pkgName
            enabled = true
            // flags == 0 → not a system app, not debuggable: renders as a normal user app.
            flags = 0
            sourceDir = sourceDirPath
        }
    }
    override val label: CaString = displayLabel.toCaString()
    override val icon: ((Context) -> Drawable)? = null
    override val installerInfo: InstallerInfo = InstallerInfo()
}

internal fun previewInstalled(
    pkgName: String = "com.example.app",
    label: String = "Example App",
    versionName: String = "1.0.0",
    versionCode: Long = 42L,
    installedAt: Instant = Instant.parse("2026-01-15T09:00:00Z"),
    updatedAt: Instant = Instant.parse("2026-04-01T12:00:00Z"),
    sourceDir: String? = "/data/app/com.example.app/base.apk",
): Installed = PreviewInstalled(
    pkgName = pkgName,
    displayLabel = label,
    versionName = versionName,
    versionCodeValue = versionCode,
    installedAt = installedAt,
    updatedAt = updatedAt,
    sourceDirPath = sourceDir,
)

internal fun previewSizeStats(
    appBytes: Long = 64L * 1024 * 1024,
    cacheBytes: Long = 12L * 1024 * 1024,
    dataBytes: Long = 48L * 1024 * 1024,
): PkgOps.SizeStats = PkgOps.SizeStats(
    appBytes = appBytes,
    cacheBytes = cacheBytes,
    externalCacheBytes = null,
    dataBytes = dataBytes,
)

internal fun previewAppInfo(
    pkg: Installed = previewInstalled(),
    isActive: Boolean? = false,
    sizes: PkgOps.SizeStats? = previewSizeStats(),
    canBeToggled: Boolean = true,
    canBeStopped: Boolean = true,
    canBeExported: Boolean = true,
    canBeDeleted: Boolean = true,
    canBeArchived: Boolean = false,
    canBeRestored: Boolean = false,
): AppInfo = AppInfo(
    pkg = pkg,
    isActive = isActive,
    sizes = sizes,
    // Keep usage null: UsageInfo.screenTimeSince does stats.minOf{} and crashes on empty stats.
    usage = null,
    userProfile = null,
    canBeToggled = canBeToggled,
    canBeStopped = canBeStopped,
    canBeExported = canBeExported,
    canBeDeleted = canBeDeleted,
    canBeArchived = canBeArchived,
    canBeRestored = canBeRestored,
)

internal fun previewAppControlRow(
    appInfo: AppInfo = previewAppInfo(),
): AppControlListViewModel.Row = AppControlListViewModel.Row(
    appInfo = appInfo,
    sectionKeyName = "E",
    sectionKeyPkg = appInfo.pkg.packageName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
)
