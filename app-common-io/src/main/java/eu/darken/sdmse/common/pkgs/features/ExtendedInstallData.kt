package eu.darken.sdmse.common.pkgs.features

import android.content.pm.ApplicationInfo
import java.time.Instant

interface ExtendedInstallData : PkgInfo {

    val isEnabled: Boolean
        get() = applicationInfo?.enabled ?: true

    val isSystemApp: Boolean
        get() = applicationInfo?.run { flags and ApplicationInfo.FLAG_SYSTEM != 0 } ?: true

    val isUpdatedSystemApp: Boolean
        get() = applicationInfo?.run { flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 } ?: true

    val installedAt: Instant?
        get() = packageInfo.firstInstallTime.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }

    val updatedAt: Instant?
        get() = packageInfo.lastUpdateTime.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }

    val installerInfo: InstallerInfo
}