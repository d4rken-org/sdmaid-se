package eu.darken.sdmse.main.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.MimeTypes
import eu.darken.sdmse.common.backup.BackupCodec
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

    // Survives config changes (VM-scoped); on process death the user simply re-picks the file.
    // @Volatile: set/read/cleared from separate launch{} coroutines that may run on different threads.
    @Volatile private var pendingEnvelope: BackupEnvelope? = null

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
            type = MIME_GZIP
            putExtra(Intent.EXTRA_TITLE, "SDMaidSE-backup-${LocalDate.now()}.json.gz")
        }
        events.emit(Event.PickExportTarget(intent))
    }

    fun performExport(uri: Uri?) = launch {
        if (uri == null) {
            log(TAG, WARN) { "Export cancelled, no target picked" }
            return@launch
        }
        log(TAG) { "performExport($uri)" }
        val raw = configBackupManager.createBackup()
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(BackupCodec.encode(raw))
        } ?: throw IOException("Failed to open output stream for $uri")
        log(TAG, INFO) { "Backup written to $uri" }
        events.emit(Event.ExportDone)
    }

    fun requestImport() = launch {
        log(TAG) { "requestImport()" }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Accept both the gzip backups we now write and older/plain .json backups.
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(MIME_GZIP, MimeTypes.Json.value, "application/octet-stream"))
        }
        events.emit(Event.PickImportSource(intent))
    }

    fun onImportPicked(uri: Uri?) = launch {
        if (uri == null) {
            log(TAG, WARN) { "Import cancelled, no source picked" }
            return@launch
        }
        log(TAG) { "onImportPicked($uri)" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw InvalidBackupException("Failed to read backup file")
        val raw = BackupCodec.decode(bytes)
        val envelope = configBackupManager.parse(raw)
        pendingEnvelope = envelope
        events.emit(Event.ConfirmRestore(envelope.toConfirmInfo()))
    }

    fun confirmRestore(mode: RestoreMode) = launch {
        val envelope = pendingEnvelope ?: run {
            log(TAG, WARN) { "confirmRestore() with no staged backup" }
            return@launch
        }
        log(TAG, INFO) { "confirmRestore(mode=$mode)" }
        val result = configBackupManager.restore(envelope, mode)
        pendingEnvelope = null
        events.emit(Event.RestoreDone(failedSections = result.failures.size))
    }

    fun cancelRestore() {
        log(TAG) { "cancelRestore()" }
        pendingEnvelope = null
    }

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
        data object ExportDone : Event
        data class RestoreDone(val failedSections: Int) : Event
    }

    companion object {
        private val TAG = logTag("Backup", "Restore", "ViewModel")
        private const val MIME_GZIP = "application/gzip"
    }
}
