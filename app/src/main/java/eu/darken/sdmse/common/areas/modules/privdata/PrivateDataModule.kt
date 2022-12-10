package eu.darken.sdmse.common.areas.modules.privdata

import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.hasFlags
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.user.UserManager2
import timber.log.Timber
import java.io.IOException
import java.util.regex.Pattern
import javax.inject.Inject

@Reusable
class PrivateDataModule @Inject constructor(
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
) : DataAreaModule {

    private val mirrorStorage = LocalPath.build("/data_mirror")

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG, INFO) { "LocalGateway has no root, skipping." }
            return emptySet()
        }

        return if (hasApiLevel(Build.VERSION_CODES.R) && gateway.exists(mirrorStorage, mode = LocalGateway.Mode.ROOT)) {
            getMirrored(gateway, firstPass)
        } else {
            determineLegacy(gateway, firstPass)
        }
    }

    /**
     * Since API30 (Android 11), /data/data is just a bind mount to hide other apps
     * https://android.googlesource.com/platform//system/core/+/3cca270e95ca8d8bc8b800e2b5d7da1825fd7100
     * Looking at /data/data will just show our own package, even with root
     * /data_mirror/data_ce/null/0
     * /data_mirror/data_de/null/0
     */
    private suspend fun getMirrored(gateway: LocalGateway, allAreas: Collection<DataArea>): Collection<DataArea> {
        val dataAreas = allAreas.filter { it.type == DataArea.Type.DATA }

        if (dataAreas.isEmpty()) {
            log(TAG, WARN) { "No data areas available." }
            return emptySet()
        }

        val pois = listOf("data_ce", "data_de")

        return gateway.listFiles(mirrorStorage, mode = LocalGateway.Mode.ROOT)
            .also { log(TAG, VERBOSE) { "Items in mirror: $it" } }
            .filter { pois.contains(it.name) }
            .map { folder ->
                userManager2.allUsers.map { user ->
                    DataArea(
                        type = DataArea.Type.PRIVATE_DATA,
                        path = LocalPath.build(mirrorStorage, folder.name, "null", "${user.handleId}"),
                        userHandle = user,
                        flags = setOf(DataArea.Flag.PRIMARY),
                    )
                }
            }
            .flatten()
    }

    // Pre Android 11, pre /data_mirror
    private suspend fun determineLegacy(
        gateway: LocalGateway,
        allAreas: Collection<DataArea>
    ): Collection<DataArea> {
        val dataStorages = allAreas.filter { it.type == DataArea.Type.DATA }

        if (dataStorages.isEmpty()) {
            log(TAG, WARN) { "No data areas available." }
            return emptySet()
        }

        val resultAreas = mutableSetOf<DataArea>()

        dataStorages
            .map { baseArea ->
                val userDirs = getPreApi30UserDirs(gateway, baseArea.path as LocalPath)

                when {
                    userDirs.isNotEmpty() -> {
                        userDirs.map {
                            DataArea(
                                type = DataArea.Type.PRIVATE_DATA,
                                path = it,
                                userHandle = userManager2.getHandleForId(it.name.toInt()),
                                flags = baseArea.flags
                            )
                        }
                    }
                    baseArea.hasFlags(DataArea.Flag.PRIMARY) && !userManager2.hasMultiUserSupport -> {
                        DataArea(
                            type = DataArea.Type.PRIVATE_DATA,
                            path = LocalPath.build(baseArea.path, "data"),
                            userHandle = userManager2.currentUser,
                            flags = setOf(DataArea.Flag.PRIMARY),
                        ).let { setOf(it) }
                    }
                    else -> {
                        log(TAG, WARN) { "Unknown base area, can't map: $baseArea" }
                        emptySet()
                    }
                }
            }
            .flatten()
            .run { resultAreas.addAll(this) }

        try {
            val path = LocalPath.build("/dbdata", "clutter")

            if (!gateway.exists(path, mode = LocalGateway.Mode.ROOT)) {
                DataArea(
                    type = DataArea.Type.PRIVATE_DATA,
                    path = gateway.lookup(path, mode = LocalGateway.Mode.ROOT),
                    userHandle = userManager2.currentUser,
                ).run { resultAreas.add(this) }
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "/dbdata/databases lookup failed: $e" }
        }

        try {
            val path = LocalPath.build("/datadata")

            if (gateway.exists(path, mode = LocalGateway.Mode.ROOT)) {
                DataArea(
                    type = DataArea.Type.PRIVATE_DATA,
                    path = gateway.lookup(path, mode = LocalGateway.Mode.ROOT),
                    userHandle = userManager2.currentUser,
                ).run { resultAreas.add(this) }
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "/dbdata/databases lookup failed: $e" }
        }

        return resultAreas
    }

    /**
     * API 29 (Android 9) and lower
     */
    private suspend fun getPreApi30UserDirs(gateway: LocalGateway, base: LocalPath): Collection<LocalPath> = try {
        val userDirs = mutableSetOf<LocalPath>()

        try {
            gateway
                .listFiles(
                    path = LocalPath.build(base, "user"),
                    mode = LocalGateway.Mode.ROOT,
                )
                .run { userDirs.addAll(this) }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to get 'user' dirs: ${e.asLog()}" }
        }

        try {
            gateway
                .listFiles(
                    path = LocalPath.build(base, "user_de"),
                    mode = LocalGateway.Mode.ROOT,
                )
                .run { userDirs.addAll(this) }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to get 'user_de' dirs: ${e.asLog()}" }
        }

        userDirs.filter { USER_DIR_NUMBER_PATTERN.matcher(it.name).matches() }
    } catch (e: IOException) {
        Timber.tag(TAG).e(e)
        emptySet()
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PrivateDataModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "PrivateData")
        private val USER_DIR_NUMBER_PATTERN = Pattern.compile("([0-9]{1,2})")
    }
}