package eu.darken.sdmse.common.areas.modules.pub

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFGateway
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.shizuku.canUseShizukuNow
import eu.darken.sdmse.common.storage.PathMapper
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.user.UserManager2
import java.io.IOException
import javax.inject.Inject

@Reusable
class SdcardsModule @Inject constructor(
    private val storageEnvironment: StorageEnvironment,
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
    private val pathMapper: PathMapper,
    private val rootManager: RootManager,
    private val shizukuManager: ShizukuManager,
) : DataAreaModule {

    @Suppress("ComplexRedundantLet")
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
        // Normal
        targetPath.let { localPath ->
            val localGateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

            val testFile = localPath.child("$TEST_FILE_PREFIX-local-$rngString")
            var fileCreated = false

            try {
                localGateway.createFile(testFile, mode = LocalGateway.Mode.NORMAL)

                fileCreated = localGateway.exists(testFile, mode = LocalGateway.Mode.NORMAL)

                if (fileCreated) {
                    log(TAG) { "Original targetPath is accessible $targetPath" }
                    return localPath
                } else {
                    log(TAG) { "Failed to create test file $testFile" }
                }
            } catch (e: IOException) {
                log(TAG, WARN) { "Couldn't create $testFile: ${e.asLog()}" }
            } finally {
                try {
                    if (fileCreated) localGateway.delete(testFile, mode = LocalGateway.Mode.NORMAL)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Clean up of $testFile failed: $e" }
                }
            }
        }

        // ADB
        targetPath.let { localPath ->
            if (!shizukuManager.canUseShizukuNow()) return@let

            val localGateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

            val testFile = localPath.child("$TEST_FILE_PREFIX-adb-$rngString")
            var fileCreated = false

            try {
                localGateway.createFile(testFile, mode = LocalGateway.Mode.ADB)

                fileCreated = localGateway.exists(testFile, mode = LocalGateway.Mode.ADB)

                if (fileCreated) {
                    log(TAG) { "Original targetPath is accessible via ADB $targetPath" }
                    return localPath
                } else {
                    log(TAG) { "Failed to create test file via ADB $testFile" }
                }
            } catch (e: IOException) {
                log(TAG, WARN) { "Couldn't create with ADB $testFile: $e" }
            } finally {
                try {
                    if (fileCreated) localGateway.delete(testFile, mode = LocalGateway.Mode.ADB)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Clean up of $testFile with ADB failed: $e" }
                }
            }
        }

        // Root
        targetPath.let { localPath ->
            if (!rootManager.canUseRootNow()) return@let

            val localGateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

            val testFile = localPath.child("$TEST_FILE_PREFIX-root-$rngString")
            var fileCreated = false

            try {
                localGateway.createFile(testFile, mode = LocalGateway.Mode.ROOT)

                fileCreated = localGateway.exists(testFile, mode = LocalGateway.Mode.ROOT)

                if (fileCreated) {
                    log(TAG) { "Original targetPath is accessible via ROOT $targetPath" }
                    return localPath
                } else {
                    log(TAG) { "Failed to create test file via ROOT $testFile" }
                }
            } catch (e: IOException) {
                log(TAG, WARN) { "Couldn't create with ROOT $testFile: $e" }
            } finally {
                try {
                    if (fileCreated) localGateway.delete(testFile, mode = LocalGateway.Mode.ROOT)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Clean up of $testFile with ROOT failed: $e" }
                }
            }
        }

        // SAF
        log(TAG) { "$targetPath wasn't accessible trying SAF mapping..." }
        pathMapper.toSAFPath(targetPath)?.let { safPath ->
            val safGateway = gatewaySwitch.getGateway(APath.PathType.SAF) as SAFGateway

            val testFile = safPath.child("$TEST_FILE_PREFIX-saf-$rngString")
            var fileCreated = false

            try {
                safGateway.createFile(testFile)

                fileCreated = safGateway.exists(testFile)

                if (fileCreated) {
                    log(TAG) { "Switching from $targetPath to $safPath" }
                    return safPath
                } else {
                    log(TAG) { "Failed to create test file via SAF $testFile" }
                }
            } catch (e: IOException) {
                log(TAG, WARN) { "Couldn't create $testFile: $e" }
            } finally {
                try {
                    if (fileCreated) safGateway.delete(testFile)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Clean up of $testFile with SAF failed: $e" }
                }
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
        val TEST_PREFIX = "eu.darken.sdmse-test"
        val TEST_FILE_PREFIX = "$TEST_PREFIX-area-access"
        val TAG: String = logTag("DataArea", "Module", "Sdcard")
    }
}