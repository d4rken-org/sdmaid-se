package eu.darken.sdmse.common.areas.modules.priv

import dagger.Reusable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.hasFlags
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
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject

/**
 * Pre Android 11, pre /data_mirror
 */
@Reusable
class PrivateDataLegacyModule @Inject constructor(
    private val userManager: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
) : DataAreaModule {

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

        val coreAreas = if (userManager.hasMultiUserSupport) {
            log(TAG) { "Device has multi user support" }
            determineMultiUser(gateway, dataStorages)
        } else {
            log(TAG) { "We are in single user mode" }
            determineSingleUser(dataStorages)
        }
        log(TAG) { "Core areas: $coreAreas" }

        val legacyAreas = determineLegacyLegancy(gateway)
        log(TAG) { "Legacy areas: $legacyAreas" }

        return coreAreas + legacyAreas
    }

    private suspend fun determineSingleUser(baseAreas: Collection<DataArea>): Collection<DataArea> {
        return baseAreas
            .filter { it.hasFlags(DataArea.Flag.PRIMARY) }
            .map { baseArea ->
                DataArea(
                    type = DataArea.Type.PRIVATE_DATA,
                    path = LocalPath.build(baseArea.path as LocalPath, "data"),
                    userHandle = userManager.currentUser().handle,
                    flags = setOf(DataArea.Flag.PRIMARY),
                )
            }
    }

    private suspend fun determineMultiUser(
        gateway: LocalGateway,
        baseAreas: Collection<DataArea>
    ): Collection<DataArea> {
        val users = userManager.allUsers()

        val deviceEncrypted = baseAreas
            .map { baseArea -> users.map { baseArea to it } }
            .flatten()
            .mapNotNull { (baseArea, user) ->
                baseArea.path as LocalPath
                val areaPath = baseArea.path.child("user_de", "${user.handle.handleId}")
                if (!areaPath.canRead(gateway)) return@mapNotNull null

                DataArea(
                    type = DataArea.Type.PRIVATE_DATA,
                    path = areaPath,
                    userHandle = user.handle,
                    flags = baseArea.flags
                )
            }
        log(TAG, VERBOSE) { "Device encrypted areas: $deviceEncrypted" }

        val credentialsEncrypted = baseAreas
            .map { baseArea -> users.map { baseArea to it } }
            .flatten()
            .mapNotNull { (baseArea, user) ->
                baseArea.path as LocalPath
                val areaPath = baseArea.path.child("user", "${user.handle.handleId}")
                if (!areaPath.canRead(gateway)) return@mapNotNull null

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
                    flags = baseArea.flags
                )
            }
        log(TAG, VERBOSE) { "Credentials encrypted areas: $credentialsEncrypted" }

        return deviceEncrypted + credentialsEncrypted
    }

    private suspend fun determineLegacyLegancy(
        gateway: LocalGateway
    ): Collection<DataArea> {
        val currentUser = userManager.currentUser().handle
        val resultAreas = mutableSetOf<DataArea>()
        try {
            val path = LocalPath.build("/dbdata", "clutter")

            if (gateway.canRead(path, mode = LocalGateway.Mode.ROOT)) {
                DataArea(
                    type = DataArea.Type.PRIVATE_DATA,
                    path = path,
                    userHandle = currentUser,
                ).run { resultAreas.add(this) }
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "/dbdata/databases lookup failed: $e" }
        }

        try {
            val path = LocalPath.build("/datadata")

            if (gateway.canRead(path, mode = LocalGateway.Mode.ROOT)) {
                DataArea(
                    type = DataArea.Type.PRIVATE_DATA,
                    path = path,
                    userHandle = currentUser,
                ).run { resultAreas.add(this) }
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "/dbdata/databases lookup failed: $e" }
        }

        return resultAreas
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "PrivateData", "Legacy")
        private val USER_DIR_NUMBER_PATTERN by lazy { Regex("([0-9]{1,2})") }
    }
}