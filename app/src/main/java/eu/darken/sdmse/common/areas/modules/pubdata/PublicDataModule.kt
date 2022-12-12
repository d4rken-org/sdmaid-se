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
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.saf.SAFGateway
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.storage.StorageManager2
import javax.inject.Inject

@Reusable
class PublicDataModule @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val storageManager2: StorageManager2
) : DataAreaModule {

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val sdcardAreas = firstPass.filter { it.type == DataArea.Type.SDCARD }

        val localGateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway
        val safGateway = gatewaySwitch.getGateway(APath.PathType.SAF) as SAFGateway

        val areas = sdcardAreas
            .filter {
                if (hasApiLevel(33) && !localGateway.hasRoot()) {
                    log(TAG, INFO) { "Skipping (API33 and no root): $it" }
                    false
//                } else if (hasApiLevel(30) && safGateway.) {
//                    log(TAG, INFO) { "Skipping (API30+ and no SAF grant): $it" }
//                    false
                } else {
                    true
                }
            }
            .map { sdcard ->
                val sdPath = sdcard.path as LocalPath
                DataArea(
                    type = DataArea.Type.PUBLIC_DATA,
                    path = sdPath.child("Android", "data"),
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