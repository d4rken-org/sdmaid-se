package eu.darken.sdmse.main.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.backup.BackupEnvelope
import eu.darken.sdmse.common.backup.ConfigBackupManager
import eu.darken.sdmse.common.backup.InvalidBackupException
import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isProSettled
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val upgradeRepo: UpgradeRepo,
    private val configBackupManager: ConfigBackupManager,
) : ViewModel4(dispatcherProvider, TAG) {

    val events = SingleEventFlow<Event>()

    // The imported archive, staged between import (dialog) and confirm. VM-scoped so it survives
    // config changes; on process death the user simply re-picks the file.
    // @Volatile: set/read/cleared from separate launch{} coroutines that may run on different threads.
    @Volatile private var pendingBackup: File? = null

    val state: StateFlow<State> = upgradeRepo.upgradeInfo
        .map { State(isPro = it.isPro) }
        .safeStateIn(initialValue = State(), onError = { State() })

    fun requestExport() = launch {
        log(TAG) { "requestExport()" }
        if (!upgradeRepo.isProSettled()) {
            log(TAG) { "Pro upgrade required for export" }
            navTo(UpgradeRoute())
            return@launch
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MIME_ZIP
            putExtra(Intent.EXTRA_TITLE, "SDMaidSE-backup-${LocalDate.now()}.zip")
        }
        events.emit(Event.PickExportTarget(intent))
    }

    fun performExport(uri: Uri?) = launch {
        if (uri == null) {
            log(TAG, WARN) { "Export cancelled, no target picked" }
            return@launch
        }
        log(TAG) { "performExport($uri)" }
        // Build the archive into a temp file first, then copy it onto the user-picked document. The SAF
        // target only receives bytes once the whole zip is complete, so a failure mid-write never leaves
        // a corrupt/partial file sitting behind a "success" toast.
        var staged: File? = null
        val result = try {
            staged = File.createTempFile("export-", ".zip", backupTmpDir())
            val res = staged.outputStream().use { configBackupManager.writeBackup(it) }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                staged.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("Failed to open output stream for $uri")
            res
        } catch (e: Exception) {
            // Best-effort cleanup of the empty/partial document so the user isn't left with a broken file.
            // Covers any failure once the target uri is accepted — including temp-file creation.
            try {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (de: Exception) {
                log(TAG, WARN) { "Could not remove failed export target $uri: ${de.message}" }
            }
            throw e
        } finally {
            staged?.delete()
        }
        log(TAG, INFO) { "Backup written to $uri (${result.failures.size} failures)" }
        events.emit(Event.ExportDone(failedSections = result.failures.map { it.key }))
    }

    fun requestImport() = launch {
        log(TAG) { "requestImport()" }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(MIME_ZIP, "application/octet-stream"))
        }
        events.emit(Event.PickImportSource(intent))
    }

    fun onImportPicked(uri: Uri?) = launch {
        if (uri == null) {
            log(TAG, WARN) { "Import cancelled, no source picked" }
            return@launch
        }
        log(TAG) { "onImportPicked($uri)" }
        // Copy the archive to a temp file so it can be read with random access (ZipFile).
        val staged = File.createTempFile("import-", ".zip", backupTmpDir())
        context.contentResolver.openInputStream(uri)?.use { input ->
            staged.outputStream().use { input.copyTo(it) }
        } ?: run {
            staged.delete()
            throw InvalidBackupException("Failed to read backup file")
        }

        val envelope = try {
            configBackupManager.parse(staged)
        } catch (e: Exception) {
            staged.delete()
            throw e
        }
        pendingBackup?.delete()
        pendingBackup = staged
        events.emit(Event.ConfirmRestore(envelope.toConfirmInfo()))
    }

    fun confirmRestore(mode: RestoreMode) = launch {
        val staged = pendingBackup ?: run {
            log(TAG, WARN) { "confirmRestore() with no staged backup" }
            return@launch
        }
        log(TAG, INFO) { "confirmRestore(mode=$mode)" }
        try {
            val result = configBackupManager.restore(staged, mode)
            events.emit(Event.RestoreDone(failedSections = result.failures.map { it.key }))
        } finally {
            staged.delete()
            pendingBackup = null
        }
    }

    fun cancelRestore() {
        log(TAG) { "cancelRestore()" }
        pendingBackup?.delete()
        pendingBackup = null
    }

    private fun backupTmpDir(): File = File(context.cacheDir, "backup").apply { mkdirs() }

    private fun BackupEnvelope.toConfirmInfo(): RestoreConfirmInfo {
        val currentDevice = "${Build.MANUFACTURER} ${Build.MODEL}"
        val sourceDevice = "$deviceManufacturer $deviceModel"
        val currentAndroid = Build.VERSION.RELEASE ?: "?"
        val currentFlavor = BuildConfigWrap.FLAVOR.name
        return RestoreConfirmInfo(
            createdAt = createdAt.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)),
            sourceVersion = appVersionName,
            currentVersion = BuildConfigWrap.VERSION_NAME,
            versionMatches = appVersionName == BuildConfigWrap.VERSION_NAME,
            sourceAndroid = androidRelease,
            currentAndroid = currentAndroid,
            androidDiffers = androidRelease != currentAndroid,
            sourceDevice = sourceDevice,
            currentDevice = currentDevice,
            deviceDiffers = sourceDevice != currentDevice,
            sourceFlavor = flavor,
            currentFlavor = currentFlavor,
            flavorDiffers = flavor != currentFlavor,
        )
    }

    data class State(
        val isPro: Boolean? = null,
    )

    data class RestoreConfirmInfo(
        val createdAt: String,
        val sourceVersion: String,
        val currentVersion: String,
        val versionMatches: Boolean,
        val sourceAndroid: String,
        val currentAndroid: String,
        val androidDiffers: Boolean,
        val sourceDevice: String,
        val currentDevice: String,
        val deviceDiffers: Boolean,
        val sourceFlavor: String,
        val currentFlavor: String,
        val flavorDiffers: Boolean,
    )

    sealed interface Event {
        data class PickExportTarget(val intent: Intent) : Event
        data class PickImportSource(val intent: Intent) : Event
        data class ConfirmRestore(val info: RestoreConfirmInfo) : Event
        data class ExportDone(val failedSections: List<String>) : Event
        data class RestoreDone(val failedSections: List<String>) : Event
    }

    companion object {
        private val TAG = logTag("Backup", "Restore", "ViewModel")
        private const val MIME_ZIP = "application/zip"
    }
}
