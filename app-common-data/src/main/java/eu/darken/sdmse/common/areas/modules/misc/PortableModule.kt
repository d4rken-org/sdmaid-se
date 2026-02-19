package eu.darken.sdmse.common.areas.modules.misc

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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.toLocalPath
import eu.darken.sdmse.common.files.saf.SAFGateway
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.storage.PathMapper
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.user.UserManager2
import java.io.IOException
import javax.inject.Inject


@Reusable
class PortableModule @Inject constructor(
    private val userManager2: UserManager2,
    private val storageManager2: StorageManager2,
    private val gatewaySwitch: GatewaySwitch,
    private val pathMapper: PathMapper,
    private val adbManager: AdbManager,
    private val rootManager: RootManager,
) : DataAreaModule {

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val volumeInfos = storageManager2.volumes ?: emptySet()
        log(TAG) { "VolumeInfos: $volumeInfos" }

        val storageVolumes = storageManager2.storageVolumes
        log(TAG) { "StorageVolumes: $storageVolumes" }

        val portables = storageVolumes
            .filter { !it.isPrimary && it.isRemovable }
            .filter { it.directory != null }
            .filter { cand ->
                val isPubStorage = firstPass.any { it.path == cand.directory!!.toLocalPath() }
                if (isPubStorage) {
                    log(TAG) { "$cand is a public storage that is already covered" }
                }
                !isPubStorage
            }
            .filter { cand ->
                val volumeInfoX = volumeInfos.singleOrNull { it.path == cand.directory }
                val isUsbStorage = volumeInfoX?.disk?.isUsb
                if (isUsbStorage == null) log(TAG, WARN) { "DiskInfo.isUsb is NULL for $cand" }
                isUsbStorage == true
            }

        // In the happy path. we just get 3 volumes, one is primary and internal, two are removable and not primary
        // One of them may be an sdcard, the other one would be USB
        // If we remove the sdcard, we have the USB left. The sdcard should be identifiable by getting externalCacheDirs

        return portables
            .map { it.directory!!.toLocalPath() }
            .mapNotNull {
                val accessPath = determineAreaAccessPath(it)
                if (accessPath != null) {
                    log(TAG, INFO) { "Got a portable storage under $accessPath" }
                } else {
                    log(TAG, INFO) { "Got a portable storage but it's not accessible ($it)" }
                }
                accessPath
            }
            .map { accessPath ->
                DataArea(
                    type = DataArea.Type.PORTABLE,
                    path = accessPath,
                    userHandle = userManager2.systemUser().handle,
                )
            }
    }

    private suspend fun determineAreaAccessPath(targetPath: LocalPath): APath? {
        // Normal
        targetPath.let { localPath ->
            val localGateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

            val testFile = localPath.child("${TEST_FILE_PREFIX}-local-$rngString")
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
            if (!adbManager.canUseAdbNow()) return@let

            val localGateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

            val testFile = localPath.child("${TEST_FILE_PREFIX}-adb-$rngString")
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

            val testFile = localPath.child("${TEST_FILE_PREFIX}-root-$rngString")
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

            val testFile = safPath.child("${TEST_FILE_PREFIX}-saf-$rngString")
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

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PortableModule): DataAreaModule
    }

    companion object {
        val TEST_PREFIX = "eu.darken.sdmse-test-usb"
        val TEST_FILE_PREFIX = "$TEST_PREFIX-area-access"
        val TAG: String = logTag("DataArea", "Module", "Portable")
    }
}