package eu.darken.sdmse.common.pkgs

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PermissionInfo

interface NormalPkg : Pkg {

    val applicationInfo: ApplicationInfo?

    val isSystemApp: Boolean

    val sourceDir: String?

    val installLocation: Int

    val firstInstallTime: Long

    val lastUpdateTime: Long

    val versionName: String?

    val activities: Collection<ActivityInfo>?

    val receivers: Collection<ActivityInfo>?

    val permissions: Collection<PermissionInfo>?

    val requestedPermissions: Collection<String>?

}
