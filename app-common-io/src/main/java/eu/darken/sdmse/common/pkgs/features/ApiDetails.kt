package eu.darken.sdmse.common.pkgs.features

import android.os.Build
import eu.darken.sdmse.common.hasApiLevel

interface ApiDetails : PkgInfo {

    val apiTargetLevel: Int?
        get() = applicationInfo?.targetSdkVersion

    val apiCompileLevel: Int?
        get() = if (hasApiLevel(Build.VERSION_CODES.S)) applicationInfo?.compileSdkVersion else null

    val apiMinimumLevel: Int?
        get() = if (hasApiLevel(Build.VERSION_CODES.N)) applicationInfo?.minSdkVersion else null

}