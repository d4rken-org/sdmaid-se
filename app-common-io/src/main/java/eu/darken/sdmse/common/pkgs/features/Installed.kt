package eu.darken.sdmse.common.pkgs.features

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.user.UserHandle2
import java.time.Instant

interface Installed : Pkg {
    val packageInfo: PackageInfo

    val userHandles: Set<UserHandle2>

    val isSystemApp: Boolean
        get() = packageInfo.applicationInfo?.run { flags and ApplicationInfo.FLAG_SYSTEM != 0 } ?: true

    val installedAt: Instant?
        get() = packageInfo.firstInstallTime.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }

    val updatedAt: Instant?
        get() = packageInfo.lastUpdateTime.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }

    val installerInfo: InstallerInfo
}