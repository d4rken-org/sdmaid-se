package eu.darken.sdmse.common.deviceadmin

import android.app.admin.DevicePolicyManager
import dagger.Reusable
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject


@Reusable class DeviceAdminManager @Inject constructor(
    private val devicePolicyManager: DevicePolicyManager,
) {

    suspend fun getDeviceAdmins(): Set<Pkg.Id> = devicePolicyManager
        .activeAdmins
        ?.map { it.packageName.toPkgId() }
        ?.toSet()
        ?: emptySet()
}