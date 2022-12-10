package eu.darken.sdmse.common.areas.modules.privdata

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.StorageEnvironment
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.toLocalPath
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.wrps.storagemanager.StorageManager2
import timber.log.Timber
import javax.inject.Inject

@Reusable
class DataModule @Inject constructor(
    private val storageEnvironment: StorageEnvironment,
    private val userManager2: UserManager2,
    private val storageManager2: StorageManager2
) : DataAreaModule {

    override suspend fun firstPass(): Collection<DataArea> {
        val areas = mutableSetOf<DataArea>()

        storageEnvironment.dataDir
            .let {
                DataArea(
                    type = DataArea.Type.DATA,
                    path = it,
                    userHandle = userManager2.currentUser,
                    flags = setOf(DataArea.Flag.PRIMARY)
                )
            }
            .run { areas.add(this) }

        try {
            storageManager2.volumes
                ?.also { log(TAG, VERBOSE) { "Checking $it" } }
                ?.mapNotNull { volume ->
                    if (!volume.isPrivate || volume.id?.startsWith("private:") != true || !volume.isMounted) {
                        return@mapNotNull null
                    }

                    val path = volume.path?.toLocalPath() ?: return@mapNotNull null

                    DataArea(
                        type = DataArea.Type.DATA,
                        path = path,
                        userHandle = userManager2.currentUser,
                        flags = setOf(DataArea.Flag.SECONDARY)
                    )
                }
                ?.run { areas.addAll(this) }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
        }

        log(TAG, VERBOSE) { "firstPass(): $areas" }

        return areas
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: DataModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "Data")
    }
}