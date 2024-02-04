package eu.darken.sdmse.common.areas.modules.pub

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.canRead
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.lookup
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.shizuku.canUseShizukuNow
import eu.darken.sdmse.common.storage.PathMapper
import java.io.IOException
import javax.inject.Inject

@Reusable
class PublicObbModule @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val pathMapper: PathMapper,
    private val shizukuManager: ShizukuManager,
    private val rootManager: RootManager,
) : DataAreaModule {

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val sdcardAreas = firstPass.filter { it.type == DataArea.Type.SDCARD }

        val areas = sdcardAreas
            .mapNotNull { parentArea ->
                val accessPath = try {
                    parentArea.determineAndroidObb()
                } catch (e: IOException) {
                    log(TAG, WARN) { "Failed to determine accessPath for $parentArea: ${e.asLog()}" }
                    null
                }

                accessPath?.let { parentArea to it }
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
                    type = DataArea.Type.PUBLIC_OBB,
                    path = path,
                    flags = parentArea.flags,
                    userHandle = parentArea.userHandle,
                )
            }

        log(TAG, VERBOSE) { "secondPass(): $areas" }

        return areas
    }

    private suspend fun DataArea.determineAndroidObb(): APath? {
        val parentArea = this
        val target = this.path.child("Android", "obb")

        return when {
            hasApiLevel(33) -> {
                when {
                    // If we have root, we need to convert any SAFPath back
                    rootManager.canUseRootNow() || shizukuManager.canUseShizukuNow() -> {
                        when (target) {
                            is LocalPath -> target
                            is SAFPath -> pathMapper.toLocalPath(target)
                            else -> null
                        }
                    }

                    else -> {
                        log(TAG, INFO) { "Skipping Android/obb (API33, no root/Shizuku): $parentArea" }
                        null
                    }
                }
            }

            hasApiLevel(30) -> {
                when {
                    // Can't use SAFPath with Shizuku or Root
                    rootManager.canUseRootNow() || shizukuManager.canUseShizukuNow() -> when (target) {
                        is SAFPath -> pathMapper.toLocalPath(target)
                        else -> target
                    }
                    // On API30 we can do the direct SAF grant workaround
                    else -> when (target) {
                        is LocalPath -> pathMapper.toSAFPath(target)
                        is SAFPath -> target
                        else -> null
                    }
                }
            }

            else -> target
        }
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PublicObbModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "Public", "Obb")
    }
}