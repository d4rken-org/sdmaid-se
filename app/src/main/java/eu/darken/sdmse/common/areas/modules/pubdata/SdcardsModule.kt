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
import eu.darken.sdmse.common.files.core.*
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.storage.SAFMapper
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.user.UserManager2
import java.io.IOException
import javax.inject.Inject

@Reusable
class SdcardsModule @Inject constructor(
    private val storageEnvironment: StorageEnvironment,
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
    private val safMapper: SAFMapper,
    private val rootManager: RootManager,
) : DataAreaModule {

    @Suppress("ComplexRedundantLet")
    override suspend fun firstPass(): Collection<DataArea> {
        val sdcards = mutableSetOf<DataArea>()

        // TODO we are not getting multiuser sdcards
        storageEnvironment.getPublicPrimaryStorage(userManager2.currentUser)
            .let { determineAreaAccessPath(it) }
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
            .mapNotNull { determineAreaAccessPath(it) }
            .map {
                DataArea(
                    path = it,
                    type = DataArea.Type.SDCARD,
                    userHandle = userManager2.currentUser,
                    flags = emptySet(),
                )
            }
            .run { sdcards.addAll(this) }

        if (sdcards.isEmpty()) Bugs.report(IllegalStateException("No sdcards found."))

        log(TAG, VERBOSE) { "firstPass():$sdcards" }

        return sdcards
    }

    private suspend fun determineAreaAccessPath(targetPath: LocalPath): APath? {
        // Normal
        targetPath.let { localPath ->
            val testFileLocal = localPath.child("$TEST_FILE_PREFIX-local-$rngString")
            try {
                require(!testFileLocal.exists(gatewaySwitch)) { "Our 'random' testfile already exists? ($testFileLocal)" }

                testFileLocal.createFileIfNecessary(gatewaySwitch)
                if (testFileLocal.exists(gatewaySwitch)) {
                    log(TAG) { "Original targetPath is accessible $targetPath" }
                    return localPath
                }
            } catch (e: IOException) {
                log(TAG, WARN) { "Couldn't create $testFileLocal: $e" }
            } finally {
                try {
                    if (testFileLocal.exists(gatewaySwitch)) testFileLocal.delete(gatewaySwitch)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Clean up of $testFileLocal failed: $e" }
                }
            }
        }

        // Root
        targetPath.let { localPath ->
            if (!rootManager.useRoot()) return@let

            val localGateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

            val testFileLocal = localPath.child("$TEST_FILE_PREFIX-local-$rngString")
            try {
                require(!localGateway.exists(testFileLocal, mode = LocalGateway.Mode.ROOT)) {
                    "Our 'random' testfile already exists? ($testFileLocal)"
                }

                localGateway.createFile(testFileLocal, mode = LocalGateway.Mode.ROOT)
                if (localGateway.exists(testFileLocal, mode = LocalGateway.Mode.ROOT)) {
                    log(TAG) { "Original targetPath is accessible via ROOT $targetPath" }
                    return localPath
                }
            } catch (e: IOException) {
                log(TAG, WARN) { "Couldn't create with ROOT $testFileLocal: $e" }
            } finally {
                try {
                    if (localGateway.exists(testFileLocal, mode = LocalGateway.Mode.ROOT)) {
                        localGateway.delete(testFileLocal, mode = LocalGateway.Mode.ROOT)
                    }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Clean up of $testFileLocal with ROOT failed: $e" }
                }
            }
        }



        log(TAG) { "$targetPath wasn't accessible trying SAF mapping..." }
        // Saf
        safMapper.toSAFPath(targetPath)?.let { safPath ->
            val testFileSaf = safPath.child("$TEST_FILE_PREFIX-saf-$rngString")
            try {
                require(!testFileSaf.exists(gatewaySwitch)) { "Our 'random' testfile already exists? ($testFileSaf)" }

                testFileSaf.createFileIfNecessary(gatewaySwitch)
                if (testFileSaf.exists(gatewaySwitch)) {
                    log(TAG) { "Switching from $targetPath to $safPath" }
                    return safPath
                }
            } catch (e: IOException) {
                log(TAG, WARN) { "Couldn't create $testFileSaf: $e" }
            } finally {
                try {
                    if (testFileSaf.exists(gatewaySwitch)) testFileSaf.delete(gatewaySwitch)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Clean up of $testFileSaf failed: $e" }
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