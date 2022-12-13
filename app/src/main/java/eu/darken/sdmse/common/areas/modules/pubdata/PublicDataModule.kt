package eu.darken.sdmse.common.areas.modules.pubdata

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.canRead
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.saf.SAFGateway
import eu.darken.sdmse.common.files.core.saf.SAFPath
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.storage.SAFMapper
import eu.darken.sdmse.common.storage.StorageManager2
import javax.inject.Inject

@Reusable
class PublicDataModule @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val storageManager2: StorageManager2,
    private val safMapper: SAFMapper,
) : DataAreaModule {

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val sdcardAreas = firstPass.filter { it.type == DataArea.Type.SDCARD }

        val localGateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway
        val safGateway = gatewaySwitch.getGateway(APath.PathType.SAF) as SAFGateway

        val areas = sdcardAreas
            .map { dataArea ->
                val accessPath: APath? = if (hasApiLevel(33) && !localGateway.hasRoot()) {
                    log(TAG, INFO) { "Skipping (API33 and no root): $dataArea" }
                    null
                } else if (hasApiLevel(30)) {
                    when (val target = dataArea.path.child("Android", "data")) {
                        is LocalPath -> safMapper.toSAFPath(target)
                        is SAFPath -> target
                        else -> null
                    }
                } else {
                    null
                }
                dataArea to accessPath
            }
            .filter { it.second?.canRead(gatewaySwitch) ?: false }
            .map { (sdcard, path) ->
                DataArea(
                    type = DataArea.Type.PUBLIC_DATA,
                    path = path!!,
                    flags = sdcard.flags,
                    userHandle = sdcard.userHandle,
                )
            }

        log(TAG, VERBOSE) { "secondPass(): $areas" }

        return areas
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PublicDataModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "Public", "Data")
    }
}