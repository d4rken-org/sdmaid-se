package eu.darken.sdmse.common.pkgs.features

import android.content.pm.ApplicationInfo
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import eu.darken.sdmse.common.hasApiLevel

// A Pkg where we have access to an APK
interface ReadableApk : HasPackageInfo {

    val applicationInfo: ApplicationInfo?
        get() = packageInfo.applicationInfo

    val versionName: String?
        get() = packageInfo.versionName

    val versionCode: Long
        get() = PackageInfoCompat.getLongVersionCode(packageInfo)

    val sharedUserId: String?
        get() = packageInfo.sharedUserId

    val apiTargetLevel: Int?
        get() = applicationInfo?.targetSdkVersion

    val apiCompileLevel: Int?
        get() = if (hasApiLevel(Build.VERSION_CODES.S)) applicationInfo?.compileSdkVersion else null

    val apiMinimumLevel: Int?
        get() = if (hasApiLevel(Build.VERSION_CODES.N)) applicationInfo?.minSdkVersion else null

    val requestedPermissions: Collection<String>
        get() = packageInfo.requestedPermissions?.toSet() ?: emptySet()

    val declaredPermissions: Collection<PermissionInfo>
        get() = packageInfo.permissions?.toSet() ?: emptySet()
}
