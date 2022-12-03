package eu.darken.sdmse.common.areas.modules.pubdata

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import javax.inject.Inject

@Reusable
class PublicMediaModule @Inject constructor() : DataAreaModule {

    override suspend fun firstPass(): Collection<DataArea> = emptySet()

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val sdcardAreas = firstPass.filter { it.type == DataArea.Type.SDCARD }

        val areas = sdcardAreas.map { sdcard ->
            val sdPath = sdcard.path as LocalPath
            DataArea(
                type = DataArea.Type.PUBLIC_MEDIA,
                path = sdPath.child("Android", "media"),
                flags = sdcard.flags,
                userHandle = sdcard.userHandle,
            )
        }

        log(TAG, Logging.Priority.VERBOSE) { "secondPass(): $areas" }

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