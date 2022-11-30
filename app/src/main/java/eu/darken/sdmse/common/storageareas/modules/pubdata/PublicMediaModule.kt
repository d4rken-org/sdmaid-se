package eu.darken.sdmse.common.storageareas.modules.pubdata

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.storageareas.StorageArea
import eu.darken.sdmse.common.storageareas.modules.DataAreaModule
import javax.inject.Inject

@Reusable
class PublicMediaModule @Inject constructor() : DataAreaModule {

    override suspend fun firstPass(): Collection<StorageArea> = emptySet()

    override suspend fun secondPass(firstPass: Collection<StorageArea>): Collection<StorageArea> {
        val sdcardAreas = firstPass.filter { it.type == StorageArea.Type.SDCARD }

        val areas = sdcardAreas.map { sdcard ->
            val sdPath = sdcard.path as LocalPath
            StorageArea(
                type = StorageArea.Type.PUBLIC_MEDIA,
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
        val TAG: String = logTag("StorageArea", "Module", "Public", "Media")
    }
}