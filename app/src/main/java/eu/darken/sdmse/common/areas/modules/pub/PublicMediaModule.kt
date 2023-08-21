package eu.darken.sdmse.common.areas.modules.pub

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.canRead
import eu.darken.sdmse.common.files.canWrite
import eu.darken.sdmse.common.files.lookup
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject

@Reusable
class PublicMediaModule @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val deviceDetective: DeviceDetective,
    private val storageEnv: StorageEnvironment,
    private val userManager2: UserManager2,
) : DataAreaModule {

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val areas = mutableSetOf<DataArea>()

        val sdcardAreas = firstPass.filter { it.type == DataArea.Type.SDCARD }.toMutableSet()

        sdcardAreas
            .map { parentArea ->
                parentArea to parentArea.path.child("Android", "media")
            }
            .filter { (area, path) ->
                if (!path.canRead(gatewaySwitch)) {
                    log(TAG) { "Can't read $area" }
                    return@filter false
                }

                try {
                    path.lookup(gatewaySwitch)
                    true
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to lookup $area: ${e.asLog()}" }
                    false
                }
            }
            .map { (parentArea, path) ->
                DataArea(
                    type = DataArea.Type.PUBLIC_MEDIA,
                    path = path,
                    flags = parentArea.flags,
                    userHandle = parentArea.userHandle,
                )
            }
            .run { areas.addAll(this) }

        if (sdcardAreas.isEmpty() && deviceDetective.isAndroidTV()) {
            log(TAG, INFO) { "AndroidTV: Restricted access. Trying manual data area creation." }
            userManager2.allUsers()
                .map { profile ->
                    storageEnv.getPublicStorage(profile.handle).map {
                        profile to it.child("Android", "media")
                    }
                }
                .flatten()
                .filter { (_, path) ->
                    val canRead = path.canWrite(gatewaySwitch)
                    if (!canRead) log(TAG) { "Can't write $path" }
                    canRead
                }
                .map { (profile, path) ->
                    DataArea(
                        type = DataArea.Type.PUBLIC_MEDIA,
                        path = path,
                        userHandle = profile.handle,
                    )
                }
                .run { areas.addAll(this) }
        }

        log(TAG, VERBOSE) { "secondPass(): $areas" }

        return areas
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PublicMediaModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "Public", "Media")
    }
}