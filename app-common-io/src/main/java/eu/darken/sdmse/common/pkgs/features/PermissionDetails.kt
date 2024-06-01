package eu.darken.sdmse.common.pkgs.features

import android.content.pm.PermissionInfo

interface PermissionDetails : PkgInfo {

    val requestedPermissions: Collection<String>
        get() = packageInfo.requestedPermissions?.toSet() ?: emptySet()

    val declaredPermissions: Collection<PermissionInfo>
        get() = packageInfo.permissions?.toSet() ?: emptySet()

}