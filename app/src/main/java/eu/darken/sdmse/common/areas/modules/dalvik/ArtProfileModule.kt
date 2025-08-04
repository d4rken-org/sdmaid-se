package eu.darken.sdmse.common.areas.modules.dalvik

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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.canRead
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject

@Reusable
class ArtProfileModule @Inject constructor(
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
) : DataAreaModule {

    private val mirrorArea = LocalPath.build("/data_mirror")

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG, INFO) { "LocalGateway has no root, skipping." }
            return emptySet()
        }

        return if (hasApiLevel(Build.VERSION_CODES.R) && gateway.exists(mirrorArea, mode = LocalGateway.Mode.ROOT)) {
            getMirror(gateway)
        } else {
            getLegacy(gateway, firstPass)
        }
    }

    private suspend fun getMirror(gateway: LocalGateway): Collection<DataArea> {
        val dataMirrorContent = gateway.listFiles(mirrorArea, mode = LocalGateway.Mode.ROOT)
        log(TAG, VERBOSE) { "Items in data_mirror: $dataMirrorContent" }

        val users = userManager2.allUsers()

        val refProfiles = run {
            val areaPath = mirrorArea.child("ref_profiles")
            if (!areaPath.canRead(gateway)) {
                log(TAG, WARN) { "Can't read $areaPath" }
                return@run null
            }

            DataArea(
                type = DataArea.Type.ART_PROFILE,
                path = areaPath,
                userHandle = userManager2.systemUser().handle,
            )
        }
        log(TAG, VERBOSE) { "Reference profiles area: $refProfiles" }

        val curProfiles = users.mapNotNull { user ->
            val areaPath = mirrorArea.child("cur_profiles", "${user.handle.handleId}")
            if (!areaPath.canRead(gateway)) {
                log(TAG, WARN) { "Can't read $areaPath" }
                return@mapNotNull null
            }

            DataArea(
                type = DataArea.Type.ART_PROFILE,
                path = areaPath,
                userHandle = user.handle,
            )
        }

        log(TAG, VERBOSE) { "Current profiles area: $curProfiles" }

        return setOfNotNull(refProfiles) + curProfiles
    }

    private suspend fun getLegacy(gateway: LocalGateway, firstPass: Collection<DataArea>): Collection<DataArea> {
        val dataStorages = firstPass.filter { it.type == DataArea.Type.DATA }

        if (dataStorages.isEmpty()) {
            log(TAG, WARN) { "No data areas available." }
            return emptySet()
        }

        return firstPass
            .filter { it.type == DataArea.Type.DATA }
            .filter { it.hasFlags(DataArea.Flag.PRIMARY) }
            .map { LocalPath.build(it.path as LocalPath, "misc", "profiles") }
            .map { baseDir ->
                val ref = DataArea(
                    type = DataArea.Type.ART_PROFILE,
                    userHandle = userManager2.systemUser().handle,
                    path = baseDir.child("ref"),
                )

                val usrs = userManager2.allUsers().map { usr ->
                    DataArea(
                        type = DataArea.Type.ART_PROFILE,
                        userHandle = usr.handle,
                        path = baseDir.child("cur", "${usr.handle.handleId}")
                    )
                }
                usrs + ref
            }
            .flatten()
            .filter {
                val path = it.path as LocalPath
                val exists = gateway.exists(path, mode = LocalGateway.Mode.ROOT)
                val canRead = gateway.canRead(path, mode = LocalGateway.Mode.ROOT)
                log(TAG) { "exists=$exists canRead=$canRead $it" }
                exists && canRead
            }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ArtProfileModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "ArtProfile")
    }
}