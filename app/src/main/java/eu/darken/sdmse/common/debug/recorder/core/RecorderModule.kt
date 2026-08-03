package eu.darken.sdmse.common.debug.recorder.core

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.SDMId
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.DebugSettings
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.getPackageInfo
import eu.darken.sdmse.common.upgrade.UpgradeDiagnostics
import eu.darken.sdmse.main.core.CurriculumVitae
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RecorderModule @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val dataAreaManager: DataAreaManager,
    private val sdmId: SDMId,
    private val debugSettings: DebugSettings,
    private val curriculumVitae: CurriculumVitae,
    private val upgradeDiagnostics: UpgradeDiagnostics,
    private val recorderProvider: Provider<Recorder>,
) {

    // Test seam: the header reads below are bounded on real dispatchers, a virtual-time test cannot
    // advance the production bound. Same pattern as BillingCache.cacheTimeoutMs.
    internal var headerReadTimeoutMs: Long = HEADER_READ_TIMEOUT_MS

    // Test seam: the resume fallback measures against the wall clock, which a test cannot move.
    internal var wallClock: () -> Instant = { Instant.now() }

    private val triggerFile by lazy {
        try {
            File(context.getExternalFilesDir(null), FORCE_FILE)
        } catch (_: Exception) {
            File(
                Environment.getExternalStorageDirectory(),
                "/Android/data/${BuildConfigWrap.APPLICATION_ID}/files/$FORCE_FILE"
            )
        }
    }

    private val internalState = DynamicStateFlow(TAG, appScope + dispatcherProvider.IO) {
        val triggerFileExists = triggerFile.exists()
        val savedPath = debugSettings.recorderPath.value()
        State(
            shouldRecord = triggerFileExists || savedPath != null,
        )
    }
    val state: Flow<State> = internalState.flow

    init {
        internalState.flow
            .onEach {
                log(TAG) { "New Recorder state: $it" }

                internalState.updateBlocking {
                    if (!isRecording && shouldRecord) {
                        val savedPath = debugSettings.recorderPath.value()
                        val isResuming = savedPath != null
                        val logDir = savedPath?.let {
                            log(TAG) { "Continuing existing log: $it" }
                            File(it)
                        } ?: createRecordingDir().also {
                            log(TAG) { "Starting new log: $it" }
                            debugSettings.recorderPath.value(it.path)
                        }

                        val newRecorder = recorderProvider.get().apply { start(logDir) }

                        try {
                            if (!triggerFile.exists()) triggerFile.createNewFile()

                            logInfos()
                        } catch (e: Exception) {
                            // The recorder is already live but not yet committed to the state: an exception escaping
                            // the header would abandon it where stopRecorder() can't reach it.
                            withContext(NonCancellable) {
                                try {
                                    newRecorder.stop()
                                } catch (stopError: Exception) {
                                    e.addSuppressed(stopError)
                                }
                            }
                            throw e
                        }

                        copy(
                            recorder = newRecorder,
                            currentLogDir = logDir,
                            recordingStartedAt = if (isResuming) null else SystemClock.elapsedRealtime(),
                        )
                    } else if (!shouldRecord && isRecording) {
                        log(TAG) { "Stopping log recorder for: $currentLogDir" }
                        requireNotNull(recorder) { "Recorder was null despite isRecording" }.stop()

                        debugSettings.recorderPath.value(null)
                        if (triggerFile.exists() && !triggerFile.delete()) {
                            log(TAG, ERROR) { "Failed to delete trigger file" }
                        }

                        copy(
                            recorder = null,
                            currentLogDir = null,
                            recordingStartedAt = null,
                        )
                    } else {
                        this
                    }
                }
            }
            .catch { log(TAG, ERROR) { "Log recording failed: ${it.asLog()}" } }
            .launchIn(appScope)
    }

    private fun createRecordingDir(): File {
        val pkg = BuildConfigWrap.APPLICATION_ID
        val version = BuildConfigWrap.VERSION_CODE
        val timestamp = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())

        val logId = sdmId.id.take(4)
        var sessionDir: File? = null

        File(File(context.getExternalFilesDir(null), "debug/logs"), "${pkg}_${version}_${timestamp}_$logId").apply {
            @Suppress("SetWorldWritable", "SetWorldReadable")
            if (mkdirs()) {
                log(TAG) { "Public session dir created" }
                if (setReadable(true, false)) log(TAG) { "Session dir is readable" }
                if (setWritable(true, false)) log(TAG) { "Session dir is writeable" }
                sessionDir = this
            } else {
                log(TAG, ERROR) { "Failed to create public session dir" }
            }
        }

        if (sessionDir == null) {
            sessionDir = File(File(context.cacheDir, "debug/logs"), "${pkg}_${version}_${timestamp}_$logId").apply {
                if (mkdirs()) {
                    log(TAG) { "Private session dir created" }
                } else {
                    log(TAG, ERROR) { "Failed to create private session dir" }
                }
            }
        }

        return requireNotNull(sessionDir?.takeIf { it.exists() }) {
            "Failed to create recording directory in both external and cache locations"
        }
    }

    suspend fun startRecorder(): File {
        internalState.updateBlocking {
            copy(shouldRecord = true)
        }
        val state = internalState.flow.filter { it.isRecording }.first()
        return requireNotNull(state.currentLogDir) { "currentLogDir was null despite isRecording" }
    }

    suspend fun requestStopRecorder(): StopResult {
        val currentState = internalState.value()
        if (!currentState.isRecording) return StopResult.NotRecording

        val logDir = currentState.currentLogDir
        if (logDir != null) {
            // null discriminates a resumed session from a fresh one: elapsedRealtime() is a monotonic
            // uptime that can legitimately BE 0 right after a boot, and a sentinel that a real
            // timestamp can equal sends a fresh recording down the resume fallback.
            val startedAt = currentState.recordingStartedAt
            val elapsedMs = if (startedAt != null) {
                SystemClock.elapsedRealtime() - startedAt
            } else {
                // Fallback for resumed sessions — use file creation time
                try {
                    val attrs = withContext(dispatcherProvider.IO) {
                        Files.readAttributes(logDir.toPath(), BasicFileAttributes::class.java)
                    }
                    val duration = Duration.between(attrs.creationTime().toInstant(), wallClock())
                    if (duration.isNegative) {
                        // The wall clock rolled back across the resume (NTP correction, manual change),
                        // so the fallback can't measure this recording. Fail open like the read failure
                        // below: a bogus "too short" prompt on a long recording is the worse outcome.
                        log(TAG, WARN) { "Log dir creation time is in the future: ${attrs.creationTime()}" }
                        Long.MAX_VALUE
                    } else {
                        duration.toMillis()
                    }
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to read log dir creation time: ${e.asLog()}" }
                    Long.MAX_VALUE // Don't block stop on fallback failure
                }
            }
            if (elapsedMs < MIN_RECORDING_MS) {
                log(TAG) { "Recording too short: ${elapsedMs}ms < ${MIN_RECORDING_MS}ms" }
                return StopResult.TooShort
            }
        }

        val stoppedDir = stopRecorder() ?: return StopResult.NotRecording
        return StopResult.Stopped(sessionId = SessionId.derive(stoppedDir), logDir = stoppedDir)
    }

    suspend fun stopRecorder(): File? {
        val currentPath = internalState.value().currentLogDir ?: return null
        internalState.updateBlocking {
            copy(shouldRecord = false)
        }
        internalState.flow.filter { !it.isRecording }.first()
        return currentPath
    }

    suspend fun getCurrentLogDir(): File? = internalState.value().currentLogDir

    fun getLogDirectories(): List<File> {
        val dirs = mutableListOf<File>()

        // Primary location: external files dir
        context.getExternalFilesDir(null)?.let { externalDir ->
            File(externalDir, "debug/logs").takeIf { it.exists() }?.let { dirs.add(it) }
        }

        // Fallback location: cache dir
        File(context.cacheDir, "debug/logs").takeIf { it.exists() }?.let { dirs.add(it) }

        return dirs
    }

    private suspend fun logInfos() {
        val pkgInfo = context.getPackageInfo()
        log(TAG, INFO) { "APILEVEL: ${BuildWrap.VERSION.SDK_INT}" }
        log(TAG, INFO) { "Build.FINGERPRINT: ${BuildWrap.FINGERPRINT}" }
        log(TAG, INFO) { "Build.MANUFACTOR: ${Build.MANUFACTURER}" }
        log(TAG, INFO) { "Build.BRAND: ${Build.BRAND}" }
        log(TAG, INFO) { "Build.PRODUCT: ${Build.PRODUCT}" }
        val versionInfo = "${pkgInfo.versionName} (${PackageInfoCompat.getLongVersionCode(pkgInfo)})"
        log(TAG, INFO) { "App: ${context.packageName} - $versionInfo " }
        log(TAG, INFO) { "Build: ${BuildConfigWrap.FLAVOR}-${BuildConfigWrap.BUILD_TYPE}" }

        val installID = sdmId.id
        log(TAG, INFO) { "Install ID: $installID" }

        val locales = Resources.getSystem().configuration.locales
        log(TAG, INFO) { "App locales: $locales" }

        // The three remaining sources all talk to storage or the billing stack, and any of them can
        // wedge. Debug recording is what a user reaches for when the app is ALREADY misbehaving, so
        // they run concurrently under ONE shared deadline: a stuck or broken source degrades to
        // "unavailable" and the recording still starts.
        coroutineScope {
            val deadline = System.nanoTime() + headerReadTimeoutMs * 1_000_000L
            val areasRead = async { readHeader("Data areas") { dataAreaManager.latestState.firstOrNull() } }
            val historyRead = async { readHeader("Update history") { curriculumVitae.history.firstOrNull() } }
            val diagnosticsRead = async { readHeader("Upgrade diagnostics") { upgradeDiagnostics.debugInfo() } }

            areasRead.awaitHeader(deadline, "Data areas")?.let { areas ->
                log(TAG, INFO) { "Data areas: (${areas.value?.areas?.size})" }
                areas.value?.areas?.forEachIndexed { index, dataArea -> log(TAG, INFO) { "#$index $dataArea" } }
            }

            historyRead.awaitHeader(deadline, "Update history")?.let {
                log(TAG, INFO) { "Update history: ${it.value}" }
            }

            diagnosticsRead.awaitHeader(deadline, "Upgrade diagnostics")?.value
                ?.let { log(TAG, INFO) { "Upgrade diagnostics: $it" } }
        }
    }

    /**
     * Completion marker for a header read: tells a source that legitimately has nothing to report
     * (no data areas known yet, no diagnostics on FOSS) apart from one that failed or never answered.
     */
    private class HeaderRead<T>(val value: T)

    private suspend fun <T> readHeader(source: String, read: suspend () -> T): HeaderRead<T>? = try {
        HeaderRead(read())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "$source unavailable: ${e.asLog()}" }
        null
    }

    private suspend fun <T> Deferred<HeaderRead<T>?>.awaitHeader(
        deadlineNanos: Long,
        source: String,
    ): HeaderRead<T>? {
        if (isCompleted) return await()
        val remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000L
        val result = if (remainingMs > 0) withTimeoutOrNull(remainingMs) { await() } else null
        if (result == null && !isCompleted) {
            // Cancelled, not abandoned: the surrounding scope would wait for it anyway, which is
            // exactly the hang the deadline exists to prevent.
            cancel()
            log(TAG, WARN) { "$source unavailable, read did not finish within ${headerReadTimeoutMs}ms" }
        }
        return result
    }

    sealed class StopResult {
        data object TooShort : StopResult()
        data class Stopped(val sessionId: SessionId, val logDir: File) : StopResult()
        data object NotRecording : StopResult()
    }

    data class State(
        val shouldRecord: Boolean = false,
        internal val recorder: Recorder? = null,
        val currentLogDir: File? = null,
        internal val recordingStartedAt: Long? = null,
    ) {
        val isRecording: Boolean
            get() = recorder != null
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder", "Module")
        private const val FORCE_FILE = "force_debug_run"
        /**
         * Duration heuristic for "did you forget to reproduce the issue?". A recording stopped
         * this quickly usually contains nothing but the recorder starting and stopping, which
         * costs a support round-trip to re-request.
         *
         * It stays a prompt because short recordings can be perfectly valid: a crash is logged
         * and flushed immediately, so the reproduction is already on disk. "Stop anyway" works.
         */
        private const val MIN_RECORDING_MS = 10_000L

        // Shared budget for ALL header reads, not per source: they run concurrently.
        private const val HEADER_READ_TIMEOUT_MS = 5_000L
    }
}