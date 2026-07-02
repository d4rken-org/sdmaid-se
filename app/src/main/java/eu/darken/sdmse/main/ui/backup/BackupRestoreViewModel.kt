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
import eu.darken.sdmse.common.backup.BackupLimits
import eu.darken.sdmse.common.backup.ConfigBackupManager
import eu.darken.sdmse.common.backup.InvalidBackupException
import eu.darken.sdmse.common.backup.RestoreFailedException
import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isProSettled
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val upgradeRepo: UpgradeRepo,
    private val configBackupManager: ConfigBackupManager,
    private val limits: BackupLimits,
) : ViewModel4(dispatcherProvider, TAG) {

    val events = SingleEventFlow<Event>()

    // The imported archive, staged between import (dialog) and confirm. VM-scoped so it survives
    // config changes; on process death the user simply re-picks the file.
    // @Volatile: set/read/cleared from separate launch{} coroutines that may run on different threads.
    @Volatile private var pendingBackup: File? = null

    // User-consented schema-incompatible databases of the staged backup (disclosed in the sheet).
    @Volatile private var pendingIncompatible: Set<String> = emptySet()

    // Bumped whenever a restore attempt finished so the leftover-safety-snapshot check re-runs.
    private val recoveryCheckTrigger = MutableStateFlow(UUID.randomUUID())

    private val busyOps = MutableStateFlow(0)

    val state: StateFlow<State> = combine(
        upgradeRepo.upgradeInfo,
        recoveryCheckTrigger.map { configBackupManager.findRecoveryBackup() },
        busyOps,
    ) { upgradeInfo, recovery, busy ->
        State(isPro = upgradeInfo.isPro, recoveryBackup = recovery, isBusy = busy > 0)
    }.safeStateIn(initialValue = State(), onError = { State() })

    /** Tracks a long-running operation in [State.isBusy] so the UI can show progress + disable actions. */
    private suspend fun <T> busy(block: suspend () -> T): T {
        busyOps.update { it + 1 }
        try {
            return block()
        } finally {
            busyOps.update { (it - 1).coerceAtLeast(0) }
        }
    }

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
            putExtra(Intent.EXTRA_TITLE, "SD_Maid_SE-backup-${LocalDateTime.now().format(EXPORT_NAME_STAMP)}.zip")
        }
        events.emit(Event.PickExportTarget(intent))
    }

    fun performExport(uri: Uri?) = launch {
        if (uri == null) {
            log(TAG, WARN) { "Export cancelled, no target picked" }
            return@launch
        }
        log(TAG) { "performExport($uri)" }
        performExportBusy(uri)
    }

    private suspend fun performExportBusy(uri: Uri) = busy {
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
        busy {
            // Copy the archive to a temp file so it can be read with random access (ZipFile).
            val staged = File.createTempFile("import-", ".zip", backupTmpDir())
            val inspection = try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw InvalidBackupException("Failed to read backup file")
                input.use { ins ->
                    staged.outputStream().use { out -> ins.copyToCapped(out, limits.maxArchiveBytes) }
                }
                configBackupManager.inspect(staged)
            } catch (e: Exception) {
                staged.delete()
                throw e
            }
            stageForRestore(staged, inspection)
        }
    }

    /** Stages the leftover safety snapshot of an earlier failed/interrupted restore for re-applying. */
    fun requestRecovery() = launch {
        val recovery = configBackupManager.findRecoveryBackup() ?: run {
            log(TAG, WARN) { "requestRecovery() but no recovery backup exists" }
            recoveryCheckTrigger.value = UUID.randomUUID()
            return@launch
        }
        log(TAG, INFO) { "requestRecovery(): staging $recovery" }
        busy {
            stageForRestore(recovery, configBackupManager.inspect(recovery), isRecovery = true)
        }
    }

    private suspend fun stageForRestore(
        staged: File,
        inspection: ConfigBackupManager.BackupInspection,
        isRecovery: Boolean = false,
    ) {
        pendingBackup?.takeIf { it != staged }?.let { discardStaged(it) }
        pendingBackup = staged
        pendingIncompatible = inspection.incompatibleDatabases
        events.emit(Event.ConfirmRestore(inspection.envelope.toConfirmInfo(isRecovery, inspection)))
    }

    /**
     * Drops a staged file the VM no longer needs. Imported archives are just cached copies and are
     * deleted; a safety snapshot is NEVER deleted here — it may be the only copy of the pre-restore
     * configuration, and its lifecycle belongs to the manager (deleted once a restore succeeds).
     */
    private fun discardStaged(staged: File) {
        if (configBackupManager.isSafetySnapshot(staged)) return
        staged.delete()
    }

    fun confirmRestore(mode: RestoreMode) = launch {
        val staged = pendingBackup ?: run {
            log(TAG, WARN) { "confirmRestore() with no staged backup" }
            return@launch
        }
        log(TAG, INFO) { "confirmRestore(mode=$mode)" }
        busy {
            try {
                // A REPLACE must finish once started — leaving the screen must not cancel it halfway.
                withContext(NonCancellable) {
                    configBackupManager.restore(staged, mode, acceptedIncompatible = pendingIncompatible)
                }
                discardStaged(staged)
                clearPending()
                events.emit(Event.RestoreDone)
            } catch (e: RestoreFailedException) {
                log(TAG, ERROR) { "Restore failed: ${e.asLog()}" }
                discardStaged(staged)
                // The pre-restore safety snapshot becomes the staged backup, so Undo can re-apply it.
                // Consent doesn't carry over: a snapshot is same-device/version, nothing to skip.
                clearPending()
                pendingBackup = e.recoveryBackup
                events.emit(Event.RestoreFailed(e.failedSections, canUndo = e.recoveryBackup != null))
            } catch (e: Exception) {
                // Anything else failed before mutating (integrity, preflight, safety snapshot, busy).
                discardStaged(staged)
                clearPending()
                throw e
            } finally {
                recoveryCheckTrigger.value = UUID.randomUUID()
            }
        }
    }

    private fun clearPending() {
        pendingBackup = null
        pendingIncompatible = emptySet()
    }

    /** Re-applies the pre-restore safety snapshot staged by a failed restore. */
    fun undoRestore() = confirmRestore(RestoreMode.REPLACE)

    fun cancelRestore() {
        log(TAG) { "cancelRestore()" }
        pendingBackup?.let { discardStaged(it) }
        clearPending()
        recoveryCheckTrigger.value = UUID.randomUUID()
    }

    private fun backupTmpDir(): File = File(context.cacheDir, "backup").apply { mkdirs() }

    private fun InputStream.copyToCapped(out: OutputStream, cap: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val n = read(buffer)
            if (n == -1) break
            copied += n
            if (copied > cap) throw InvalidBackupException("Backup file is too large (max $cap bytes)")
            out.write(buffer, 0, n)
        }
    }

    private fun BackupEnvelope.toConfirmInfo(
        isRecovery: Boolean = false,
        inspection: ConfigBackupManager.BackupInspection? = null,
    ) = RestoreConfirmInfo(
        createdAt = createdAt.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)),
        version = Diff(appVersionName, BuildConfigWrap.VERSION_NAME),
        android = Diff(androidRelease, Build.VERSION.RELEASE ?: "?"),
        device = Diff("$deviceManufacturer $deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}"),
        flavor = Diff(flavor, BuildConfigWrap.FLAVOR.name),
        isRecovery = isRecovery,
        historyIncompatible = inspection?.incompatibleDatabases?.isNotEmpty() == true,
    )

    data class State(
        val isPro: Boolean? = null,
        val recoveryBackup: File? = null,
        val isBusy: Boolean = false,
    )

    /** A provenance attribute of the backup vs this device — differing ones need acknowledgement. */
    data class Diff(
        val source: String,
        val current: String,
    ) {
        val differs: Boolean get() = source != current
    }

    data class RestoreConfirmInfo(
        val createdAt: String,
        val version: Diff,
        val android: Diff,
        val device: Diff,
        val flavor: Diff,
        /** Re-applying a safety snapshot: mode is locked to REPLACE (MERGE wouldn't recover). */
        val isRecovery: Boolean = false,
        /** The backup's history databases are schema-incompatible and will be skipped (needs ack). */
        val historyIncompatible: Boolean = false,
    )

    sealed interface Event {
        data class PickExportTarget(val intent: Intent) : Event
        data class PickImportSource(val intent: Intent) : Event
        data class ConfirmRestore(val info: RestoreConfirmInfo) : Event
        data class ExportDone(val failedSections: List<String>) : Event
        data object RestoreDone : Event
        data class RestoreFailed(val failedSections: List<String>, val canUndo: Boolean) : Event
    }

    companion object {
        private val TAG = logTag("Backup", "Restore", "ViewModel")
        private const val MIME_ZIP = "application/zip"

        // Suggested export name, e.g. SD_Maid_SE-backup-2026_07_02_14_32.zip — zero-padded so
        // files sort chronologically; same-minute collisions get SAF's "(1)" suffix.
        private val EXPORT_NAME_STAMP = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")
    }
}
