package eu.darken.sdmse.common.areas.modules.pubdata

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.canRead
import eu.darken.sdmse.common.storage.SAFMapper
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject

@Reusable
class SdcardsModule @Inject constructor(
    private val storageEnvironment: StorageEnvironment,
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
    private val safMapper: SAFMapper,
) : DataAreaModule {

    override suspend fun firstPass(): Collection<DataArea> {
        val sdcards = mutableSetOf<DataArea>()

        // TODO we are not getting multiuser sdcards
        storageEnvironment.getPublicPrimaryStorage(userManager2.currentUser)
            .let { origPath ->
                if (origPath.canRead(gatewaySwitch)) {
                    origPath
                } else {
                    log(TAG, INFO) { "Can't read $origPath" }
                    val safPath = safMapper.toSAFPath(origPath)
                    if (safPath?.canRead(gatewaySwitch) == true) {
                        log(TAG, WARN) { "Switched from $origPath to $safPath" }
                        safPath
                    } else {
                        null
                    }
                }
            }
            ?.let {
                DataArea(
                    path = it,
                    type = DataArea.Type.SDCARD,
                    userHandle = userManager2.currentUser,
                    flags = setOf(DataArea.Flag.PRIMARY),
                )
            }
            ?.run { sdcards.add(this) }

        storageEnvironment.getPublicSecondaryStorage(userManager2.currentUser)
            .mapNotNull { origPath ->
                if (origPath.canRead(gatewaySwitch)) {
                    origPath
                } else {
                    log(TAG, INFO) { "Can't read $origPath" }
                    val safPath = safMapper.toSAFPath(origPath)
                    if (safPath?.canRead(gatewaySwitch) == true) {
                        log(TAG, WARN) { "Switched from $origPath to $safPath" }
                        safPath
                    } else {
                        null
                    }
                }
            }
            .map {
                DataArea(
                    path = it,
                    type = DataArea.Type.SDCARD,
                    userHandle = userManager2.currentUser,
                    flags = setOf(DataArea.Flag.SECONDARY),
                )
            }
            .run { sdcards.addAll(this) }

        if (sdcards.isEmpty()) Bugs.report(IllegalStateException("No sdcards found."))

        log(TAG, VERBOSE) { "firstPass():$sdcards" }

        return sdcards
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SdcardsModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "Sdcard")
    }
}