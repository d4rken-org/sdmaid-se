package eu.darken.sdmse.common.areas.modules.pub

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.areas.modules.probeWriteAccess
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFGateway
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.storage.PathMapper
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject

@Reusable
class SdcardsModule @Inject constructor(
    private val storageEnvironment: StorageEnvironment,
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
    private val pathMapper: PathMapper,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
) : DataAreaModule {

    override suspend fun firstPass(): Collection<DataArea> {
        val sdcards = mutableSetOf<DataArea>()

        // TODO we are not getting multiuser sdcards
        storageEnvironment.getPublicPrimaryStorage(userManager2.currentUser().handle)
            ?.let { determineAreaAccessPath(it) }
            ?.let {
                DataArea(
                    path = it,
                    type = DataArea.Type.SDCARD,
                    userHandle = userManager2.currentUser().handle,
                    flags = setOf(DataArea.Flag.PRIMARY),
                )
            }
            ?.run { sdcards.add(this) }

        // Secondary storage is not user specific, e.g. /storage/3135-3132/Android/data
        storageEnvironment.getPublicSecondaryStorage(userManager2.currentUser().handle)
            .mapNotNull { determineAreaAccessPath(it) }
            .map {
                DataArea(
                    path = it,
                    type = DataArea.Type.SDCARD,
                    userHandle = userManager2.systemUser().handle,
                    flags = emptySet(),
                )
            }
            .run { sdcards.addAll(this) }

        log(TAG, VERBOSE) { "firstPass():$sdcards" }

        return sdcards
    }

    private suspend fun determineAreaAccessPath(targetPath: LocalPath): APath? {
        val localGateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        val localProbe = targetPath.child("$TEST_FILE_PREFIX-local-$rngString")
        if (localGateway.probeWriteAccess(TAG, localProbe, LocalGateway.Mode.NORMAL)) {
            log(TAG) { "Original targetPath is accessible $targetPath" }
            return targetPath
        }

        if (adbManager.canUseAdbNow()) {
            val adbProbe = targetPath.child("$TEST_FILE_PREFIX-adb-$rngString")
            if (localGateway.probeWriteAccess(TAG, adbProbe, LocalGateway.Mode.ADB)) {
                log(TAG) { "Original targetPath is accessible via ADB $targetPath" }
                return targetPath
            }
        }

        if (rootManager.canUseRootNow()) {
            val rootProbe = targetPath.child("$TEST_FILE_PREFIX-root-$rngString")
            if (localGateway.probeWriteAccess(TAG, rootProbe, LocalGateway.Mode.ROOT)) {
                log(TAG) { "Original targetPath is accessible via ROOT $targetPath" }
                return targetPath
            }
        }

        log(TAG) { "$targetPath wasn't accessible trying SAF mapping..." }
        pathMapper.toSAFPath(targetPath)?.let { safPath ->
            val safGateway = gatewaySwitch.getGateway(APath.PathType.SAF) as SAFGateway
            val safProbe = safPath.child("$TEST_FILE_PREFIX-saf-$rngString")
            if (safGateway.probeWriteAccess(TAG, safProbe)) {
                log(TAG) { "Switching from $targetPath to $safPath" }
                return safPath
            }
        }

        return null
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SdcardsModule): DataAreaModule
    }

    companion object {
        val TEST_PREFIX = "eu.darken.sdmse-test-sd"
        val TEST_FILE_PREFIX = "$TEST_PREFIX-area-access"
        val TAG: String = logTag("DataArea", "Module", "Sdcard")
    }
}