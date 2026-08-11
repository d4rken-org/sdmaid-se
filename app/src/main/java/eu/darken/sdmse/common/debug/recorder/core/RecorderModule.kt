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
import eu.darken.sdmse.common.debug.exit.ExitInfoLogger
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val exitInfoLogger: ExitInfoLogger,
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

    // Serializes the public start/stop API: each caller holds it from publishing its request through
    // observing the outcome, so a second caller can neither clear a failure the first one is still
    // waiting for nor adopt one that isn't theirs.
    private val requestLock = Mutex()

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
                    when {
                        !isRecording && shouldRecord -> startAttempt()
                        !shouldRecord && isRecording -> stopAttempt()
                        else -> this
                    }
                }
            }
            // Start and stop failures are handled per iteration above, so only the state flow ending
            // or the scope dying gets here. A failure that reached this catch would end the reactive
            // loop for the rest of the process: no later request would ever be served again, and
            // every caller awaiting a state transition would wait forever.
            .catch { log(TAG, ERROR) { "Log recording failed: ${it.asLog()}" } }
            .launchIn(appScope)
    }

    /**
     * Starts a recorder and returns the committed state. A failure anywhere in here (including
     * [Recorder.start] itself) is rolled back and recorded in the state instead of being thrown at
     * the collector: it must stay alive, and [startRecorder] needs something to observe.
     */
    private suspend fun State.startAttempt(): State {
        // Only what THIS attempt created may be rolled back: a resume marker or log dir from an
        // earlier session belongs to that session and must survive a failed start.
        var createdLogDir: File? = null
        var createdTriggerFile = false
        var candidate: Recorder? = null

        try {
            // Inside the guard: this read talks to DataStore, so it can throw like every other step
            // here, and outside the guard that throw would end the collector instead of the attempt.
            val savedPath = debugSettings.recorderPath.value()
            val isResuming = savedPath != null

            val logDir = savedPath?.let {
                log(TAG) { "Continuing existing log: $it" }
                File(it)
            } ?: createRecordingDir().also {
                log(TAG) { "Starting new log: $it" }
                createdLogDir = it
                debugSettings.recorderPath.value(it.path)
            }

            // Bound BEFORE start(): a throw from start() itself leaves a live recorder that nothing
            // else can reach, so the rollback has to know about the candidate already.
            val newRecorder = recorderProvider.get().also { candidate = it }
            newRecorder.start(logDir)
            // The file logger is installed now: re-emit the previous process exit reasons so they
            // land in THIS recording too, including one started long after app launch.
            exitInfoLogger.logPreviousExits()

            if (!triggerFile.exists() && triggerFile.createNewFile()) createdTriggerFile = true

            logInfos()

            return copy(
                recorder = newRecorder,
                currentLogDir = logDir,
                recordingStartedAt = if (isResuming) null else SystemClock.elapsedRealtime(),
                startFailure = null,
            )
        } catch (e: Exception) {
            rollbackFailedStart(e, candidate, createdLogDir, createdTriggerFile)

            // Genuine cancellation of our scope: the collector dying with it is correct.
            currentCoroutineContext().ensureActive()

            // A FOREIGN CancellationException (e.g. a withTimeout inside the header work) is just a
            // failed start. Rethrowing it because of its type would kill the collector, which is the
            // exact wedge this guard exists to prevent.
            log(TAG, ERROR) { "Failed to start recording: ${e.asLog()}" }
            return copy(
                shouldRecord = false,
                recorder = null,
                currentLogDir = null,
                recordingStartedAt = null,
                startFailure = e,
            )
        }
    }

    /**
     * Undoes a failed start attempt. Runs [NonCancellable] because the failure may BE a cancellation,
     * and every step is guarded on its own so one broken cleanup can't skip the rest.
     */
    private suspend fun rollbackFailedStart(
        cause: Exception,
        candidate: Recorder?,
        createdLogDir: File?,
        createdTriggerFile: Boolean,
    ) = withContext(NonCancellable) {
        if (candidate != null) {
            try {
                candidate.stop()
            } catch (e: Exception) {
                cause.recordSuppressed(e)
            }
        }

        if (createdTriggerFile) {
            try {
                if (triggerFile.exists() && !triggerFile.delete()) {
                    log(TAG, ERROR) { "Failed to delete trigger file during rollback" }
                }
            } catch (e: Exception) {
                cause.recordSuppressed(e)
            }
        }

        if (createdLogDir != null) {
            try {
                debugSettings.recorderPath.value(null)
            } catch (e: Exception) {
                cause.recordSuppressed(e)
            }
            try {
                if (!createdLogDir.deleteRecursively()) {
                    log(TAG, WARN) { "Failed to delete $createdLogDir during rollback" }
                }
            } catch (e: Exception) {
                cause.recordSuppressed(e)
            }
        }
    }

    /**
     * Attaches a rollback failure to the failure being reported. The very same throwable can come
     * back out of the rollback — a recorder that fails to start and rethrows on its teardown line —
     * and [Throwable.addSuppressed] rejects self-suppression with an [IllegalArgumentException],
     * which would abort the rollback before the failure state is ever committed.
     */
    private fun Throwable.recordSuppressed(other: Throwable) {
        if (other === this) return
        try {
            addSuppressed(other)
        } catch (_: Throwable) {
            // Bookkeeping only: nothing about the report may replace the failure being reported.
        }
    }

    /**
     * Stops the recorder and returns the cleared state. The transition COMPLETES even when a step
     * fails: a [stopRecorder] caller is waiting on it, and a state that still claims to be recording
     * could never be stopped again. A leaked recorder is the lesser evil, so it is logged here.
     */
    private suspend fun State.stopAttempt(): State {
        log(TAG) { "Stopping log recorder for: $currentLogDir" }

        try {
            requireNotNull(recorder) { "Recorder was null despite isRecording" }.stop()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            log(TAG, ERROR) { "Failed to stop the recorder cleanly: ${e.asLog()}" }
        }

        try {
            // Independent of the stop above: a recorder that failed to close must not leave the
            // resume marker behind, or the next app start silently resumes into a dead session.
            debugSettings.recorderPath.value(null)
            if (triggerFile.exists() && !triggerFile.delete()) {
                log(TAG, ERROR) { "Failed to delete trigger file" }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            log(TAG, ERROR) { "Failed to clear the recording markers: ${e.asLog()}" }
        }

        return copy(
            recorder = null,
            currentLogDir = null,
            recordingStartedAt = null,
        )
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

    suspend fun startRecorder(): File = requestLock.withLock {
        // The previous attempt's failure is cleared with the request it belongs to, so this call can
        // only ever observe the outcome of its own attempt.
        internalState.updateBlocking {
            copy(shouldRecord = true, startFailure = null)
        }
        // Both outcomes are terminal for this request: waiting on isRecording ALONE is what turned a
        // failed start into a caller that waits forever.
        val state = internalState.flow.filter { it.isRecording || it.startFailure != null }.first()
        state.startFailure?.let { throw it.asCallerFailure() }
        requireNotNull(state.currentLogDir) { "currentLogDir was null despite isRecording" }
    }

    /**
     * A stored failure is never our own scope's cancellation (`ensureActive` rethrows that one), but
     * it can still BE a [CancellationException] from somewhere inside the attempt. Handing that to
     * the caller as-is unwinds them as if THEY had been cancelled: the ViewModel's exception handler
     * ignores it and the user is told nothing at all. The original stays in the state.
     */
    private fun Exception.asCallerFailure(): Exception = when (this) {
        is CancellationException -> StartFailedException(this)
        else -> this
    }

    suspend fun requestStopRecorder(): StopResult = requestLock.withLock {
        // The whole transition is serialized against the start path: checking the state outside the
        // lock let a stop request answer "not recording" for a start that was already in flight and
        // committed right after, leaving a recording nobody wanted running.
        val currentState = internalState.value()
        if (!currentState.isRecording) return@withLock StopResult.NotRecording

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
                return@withLock StopResult.TooShort
            }
        }

        val stoppedDir = stopRecorderLocked() ?: return@withLock StopResult.NotRecording
        StopResult.Stopped(sessionId = SessionId.derive(stoppedDir), logDir = stoppedDir)
    }

    suspend fun stopRecorder(): File? = requestLock.withLock { stopRecorderLocked() }

    /** Callers must already hold [requestLock] — it is not reentrant. */
    private suspend fun stopRecorderLocked(): File? {
        val currentPath = internalState.value().currentLogDir ?: return null
        internalState.updateBlocking {
            copy(shouldRecord = false)
        }
        // The stop transition always commits, even on a failing recorder, so this always settles.
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

    /** Carries a start failure that is itself a cancellation out to a caller as an ordinary error. */
    class StartFailedException(cause: CancellationException) : IllegalStateException(
        "Failed to start the recorder: ${cause.message}",
        cause,
    )

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
        /**
         * Outcome of the last start attempt, cleared by the next start request. Without it a failed
         * attempt is invisible to [startRecorder], which would then wait for a recording that is
         * never coming.
         */
        internal val startFailure: Exception? = null,
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