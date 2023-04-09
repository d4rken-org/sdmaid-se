package eu.darken.sdmse.common.areas.modules.priv

import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.canRead
import eu.darken.sdmse.common.files.exists
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject

@Reusable
class PrivateDataModule @Inject constructor(
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
    private val privateDataLegacyModule: PrivateDataLegacyModule,
) : DataAreaModule {

    private val mirrorArea = LocalPath.build("/data_mirror")

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG, INFO) { "LocalGateway has no root, skipping." }
            return emptySet()
        }

        val dataStorages = firstPass.filter { it.type == DataArea.Type.DATA }

        if (dataStorages.isEmpty()) {
            log(TAG, WARN) { "No data areas available." }
            return emptySet()
        }

        return if (hasApiLevel(Build.VERSION_CODES.R) && gateway.exists(mirrorArea, mode = LocalGateway.Mode.ROOT)) {
            getMirrored(gateway)
        } else {
            privateDataLegacyModule.secondPass(firstPass)
        }
    }

    /**
     * Since API30 (Android 11), /data/data is just a bind mount to hide other apps
     * https://android.googlesource.com/platform//system/core/+/3cca270e95ca8d8bc8b800e2b5d7da1825fd7100
     * Looking at /data/data will just show our own package, even with root
     * /data_mirror/data_ce/null/0
     * /data_mirror/data_de/null/0
     */
    private suspend fun getMirrored(gateway: LocalGateway): Collection<DataArea> {
        val dataMirrorContent = gateway.listFiles(mirrorArea, mode = LocalGateway.Mode.ROOT)
        log(TAG, VERBOSE) { "Items in mirror: $dataMirrorContent" }

        val users = userManager2.allUsers()

        val deviceEncrypted = users.mapNotNull { user ->
            val areaPath = mirrorArea.child("data_de", "null", "${user.handle.handleId}")
            if (!areaPath.canRead(gateway)) {
                log(TAG, WARN) { "Can't read $areaPath" }
                return@mapNotNull null
            }

            DataArea(
                type = DataArea.Type.PRIVATE_DATA,
                path = areaPath,
                userHandle = user.handle,
                flags = setOf(DataArea.Flag.PRIMARY)
            )
        }
        log(TAG, VERBOSE) { "Device encrypted areas: $deviceEncrypted" }

        val credentialsEncrypted = users.mapNotNull { user ->
            val areaPath = mirrorArea.child("data_ce", "null", "${user.handle.handleId}")
            if (!areaPath.canRead(gateway)) {
                log(TAG, WARN) { "Can't read $areaPath" }
                return@mapNotNull null
            }

            var isEncrypted = !user.isRunning

            if (isEncrypted && areaPath.child("android").exists(gateway)) {
                isEncrypted = false
            }

            if (isEncrypted) {
                log(TAG, WARN) { "Area was encrypted, skipping $areaPath" }
                return@mapNotNull null
            }

            DataArea(
                type = DataArea.Type.PRIVATE_DATA,
                path = areaPath,
                userHandle = user.handle,
                flags = setOf(DataArea.Flag.PRIMARY)
            )
        }

        log(TAG, VERBOSE) { "Credentials encrypted areas: $credentialsEncrypted" }

        return deviceEncrypted + credentialsEncrypted
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PrivateDataModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "PrivateData")
    }
}