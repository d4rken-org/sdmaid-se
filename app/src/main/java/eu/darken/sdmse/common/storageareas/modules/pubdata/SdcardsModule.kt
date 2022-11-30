package eu.darken.sdmse.common.storageareas.modules.pubdata

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.StorageEnvironment
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.storageareas.StorageArea
import eu.darken.sdmse.common.storageareas.modules.DataAreaModule
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject

@Reusable
class SdcardsModule @Inject constructor(
    private val storageEnvironment: StorageEnvironment,
    private val userManager2: UserManager2,
) : DataAreaModule {

    override suspend fun firstPass(): Collection<StorageArea> {
        val sdcards = mutableSetOf<StorageArea>()

        // We can't scan /storage/emulated with root for multi user sdcards, because the paths might not be visible for root users.
        // TODO we are not getting multiuser sdcards
        storageEnvironment.getPublicPrimaryStorage(userManager2.currentUser)
            .let {
                StorageArea(
                    path = it.localPath,
                    type = StorageArea.Type.SDCARD,
                    userHandle = userManager2.currentUser,
                    flags = setOf(StorageArea.Flag.PRIMARY),
                )
            }
            .run { sdcards.add(this) }

        storageEnvironment.getPublicSecondaryStorage(userManager2.currentUser)
            .map {
                StorageArea(
                    path = it.localPath,
                    type = StorageArea.Type.SDCARD,
                    userHandle = userManager2.currentUser,
                    flags = setOf(StorageArea.Flag.SECONDARY),
                )
            }
            .run { sdcards.addAll(this) }

        if (sdcards.isEmpty()) Bugs.report(IllegalStateException("No sdcards found."))

        log(TAG, VERBOSE) { "firstPass():$sdcards" }

        return sdcards
    }

    override suspend fun secondPass(firstPass: Collection<StorageArea>): Collection<StorageArea> = firstPass

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SdcardsModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("StorageArea", "Module", "Sdcard")
    }
}