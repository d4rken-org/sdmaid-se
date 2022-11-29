package eu.darken.sdmse.common.dataarea.modules.pubdata

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.dataarea.DataArea
import eu.darken.sdmse.common.dataarea.DataAreaType
import eu.darken.sdmse.common.dataarea.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import javax.inject.Inject

@Reusable
class PublicDataModule @Inject constructor() : DataAreaModule {

    override suspend fun firstPass(): Collection<DataArea> = emptySet()

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val sdcardAreas = firstPass.filter { it.type == DataAreaType.SDCARD }

        val areas = sdcardAreas.map { sdcard ->
            val sdPath = sdcard.path as LocalPath
            DataArea(
                type = DataAreaType.PUBLIC_DATA,
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
        @Binds @IntoSet abstract fun sync(mod: PublicDataModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "Public", "Data")
    }
}