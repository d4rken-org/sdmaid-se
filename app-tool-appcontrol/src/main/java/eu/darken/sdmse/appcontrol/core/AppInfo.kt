package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.appcontrol.core.export.AppExportType
import eu.darken.sdmse.appcontrol.core.usage.UsageInfo
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.isNotNullOrEmpty
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.SourceAvailable
import eu.darken.sdmse.common.pkgs.isLibrary
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.user.UserProfile2
import java.time.Instant

data class AppInfo(
    val pkg: Installed,
    val isActive: Boolean?,
    val sizes: PkgOps.SizeStats?,
    val usage: UsageInfo?,
    val userProfile: UserProfile2?,
    val canBeToggled: Boolean,
    val canBeStopped: Boolean,
    val canBeExported: Boolean,
    val canBeDeleted: Boolean,
    val canBeArchived: Boolean,
    val canBeRestored: Boolean,
) {
    val label: CaString
        get() = pkg.label ?: pkg.packageName.toCaString()

    val id: Pkg.Id
        get() = pkg.id

    val installId: InstallId
        get() = pkg.installId

    val updatedAt: Instant?
        get() = (pkg as? InstallDetails)?.updatedAt

    val installedAt: Instant?
        get() = (pkg as? InstallDetails)?.installedAt

    /**
     * Version string for display, or null when it should be hidden.
     * Dynamic shared libraries have no version code (SharedLibraryInfo.longVersion == -1);
     * the backing package's versionName isn't meaningful for the library, so drop the whole line.
     */
    val versionText: String?
        get() = when {
            pkg.isLibrary && pkg.versionCode == -1L -> null
            else -> "${pkg.versionName ?: "?"} (${pkg.versionCode})"
        }

    val exportType: AppExportType
        get() = when {
            pkg is SourceAvailable && pkg.splitSources.isNotNullOrEmpty() -> AppExportType.BUNDLE
            pkg is SourceAvailable && pkg.sourceDir != null -> AppExportType.APK
            else -> AppExportType.NONE
        }
}
