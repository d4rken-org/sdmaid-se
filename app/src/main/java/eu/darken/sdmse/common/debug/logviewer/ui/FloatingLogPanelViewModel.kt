package eu.darken.sdmse.common.debug.logviewer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.DebugSettings
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.logviewer.core.LogHistoryRecorder
import eu.darken.sdmse.common.debug.logviewer.core.LogLine
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.uix.ViewModel4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FloatingLogPanelViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val debugSettings: DebugSettings,
    private val recorder: LogHistoryRecorder,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val lifecycleStarted = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val minPriority = MutableStateFlow(recorder.minPriority)

    /** The search match the UI is currently parked on, tracked by [LogLine.id] so ring-buffer drops can't corrupt it. */
    private val currentMatchId = MutableStateFlow<Long?>(null)

    val events = SingleEventFlow<Event>()

    /** Whether the panel is on screen at all: user toggle gated by debug-mode. */
    val isRendered: StateFlow<Boolean> = combine(
        debugSettings.floatingLogVisible.flow,
        debugSettings.isDebugMode.flow,
    ) { visible, debug -> visible && debug }
        .safeStateIn(initialValue = false, onError = { false })

    // A single throttled read folds buffer + paused state together, so noisy logging-while-paused
    // can't churn State recomposition faster than the snapshot cadence.
    private val readings = recorder.changes
        .onStart { emit(Unit) }
        .throttleLatest(SNAPSHOT_THROTTLE_MS)
        .map { recorder.read() }

    val state: StateFlow<State> = combine(
        readings,
        query,
        currentMatchId,
        minPriority,
    ) { reading, q, curId, minPrio ->
        // Display filter on top of the capture threshold: raising the level instantly hides stale
        // lower-priority lines still in the buffer.
        val lines = reading.lines.atLevel(minPrio)
        val matches = matchesIn(lines, q)
        val ordinal = curId?.let { matches.indexOf(it).let { idx -> if (idx >= 0) idx + 1 else 0 } } ?: 0
        State(
            lines = lines,
            query = q,
            matchCount = matches.size,
            currentOrdinal = ordinal,
            currentMatchLineId = if (ordinal > 0) curId else null,
            isPaused = reading.isPaused,
            pausedDropCount = reading.droppedWhilePaused,
            minPriority = minPrio,
        )
    }.safeStateIn(initialValue = State(), onError = { State() })

    /** Tracks whether THIS owner currently holds the recorder, so acquire/release stay balanced. */
    private var capturing = false

    init {
        // Capture is active only when rendered AND the host is foregrounded, so a persisted
        // visible=true never leaks a globally-installed logger while backgrounded or after
        // debug-mode is switched off. onCompletion guards against the flow dying (e.g. an upstream
        // throw) leaving the recorder installed.
        combine(
            debugSettings.floatingLogVisible.flow,
            debugSettings.isDebugMode.flow,
            lifecycleStarted,
        ) { visible, debug, started -> visible && debug && started }
            .distinctUntilChanged()
            .onEach { active -> updateCapture(active) }
            .onCompletion { updateCapture(false) }
            .launchIn(vmScope)
    }

    override fun onCleared() {
        updateCapture(false)
        super.onCleared()
    }

    @Synchronized
    private fun updateCapture(active: Boolean) {
        if (active == capturing) return
        capturing = active
        if (active) recorder.acquire() else recorder.release()
    }

    fun setLifecycleStarted(started: Boolean) {
        lifecycleStarted.value = started
    }

    fun setQuery(value: String) {
        query.value = value
        // Park on the newest match so prev/next walks backwards through history from there.
        currentMatchId.value = matchesIn(visibleSnapshot(), value).lastOrNull()
    }

    fun setMinPriority(priority: Logging.Priority) {
        recorder.minPriority = priority
        minPriority.value = priority
    }

    fun nextMatch() = stepMatch(forward = true)

    fun prevMatch() = stepMatch(forward = false)

    private fun stepMatch(forward: Boolean) {
        val matches = matchesIn(visibleSnapshot(), query.value)
        if (matches.isEmpty()) return
        val idx = matches.indexOf(currentMatchId.value)
        val next = when {
            idx < 0 -> if (forward) 0 else matches.lastIndex
            forward -> (idx + 1).mod(matches.size)
            else -> (idx - 1).mod(matches.size)
        }
        currentMatchId.value = matches[next]
    }

    fun togglePause() {
        recorder.setPaused(!recorder.isPaused)
    }

    fun clearBuffer() {
        recorder.clear()
        currentMatchId.value = null
    }

    fun copyAll() = launch {
        val text = visibleSnapshot().joinToString("\n") { it.render() }
        withContext(dispatcherProvider.Main) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
            events.emit(Event.Copied)
        }
    }

    fun shareAll() = launch {
        // Always go via a temp file + FileProvider so a large buffer can't blow the binder limit.
        val intent = withContext(dispatcherProvider.IO) {
            val dir = File(context.cacheDir, "debug/logs").apply { mkdirs() }
            // Fixed filename: overwritten each share so temp files don't accumulate in the cache.
            val file = File(dir, "logview.txt")
            file.writeText(visibleSnapshot().joinToString("\n") { it.render() })
            val uri = FileProvider.getUriForFile(context, "${BuildConfigWrap.APPLICATION_ID}.provider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_SUBJECT, "${BuildConfigWrap.APPLICATION_ID} LogView")
            }
        }
        events.emit(Event.LaunchShare(Intent.createChooser(intent, context.getString(R.string.debug_logview_share_label))))
    }

    fun onShareLaunchFailed(error: Throwable) {
        errorEvents.tryEmit(error)
    }

    fun close() = launch {
        debugSettings.floatingLogVisible.value(false)
    }

    private fun visibleSnapshot(): List<LogLine> = recorder.snapshot().atLevel(minPriority.value)

    private fun List<LogLine>.atLevel(min: Logging.Priority): List<LogLine> =
        filter { it.priority.intValue >= min.intValue }

    private fun matchesIn(lines: List<LogLine>, q: String): List<Long> =
        if (q.isBlank()) {
            emptyList()
        } else {
            lines.asSequence()
                .filter { it.message.contains(q, ignoreCase = true) }
                .map { it.id }
                .toList()
        }

    sealed interface Event {
        data class LaunchShare(val intent: Intent) : Event
        data object Copied : Event
    }

    data class State(
        val lines: List<LogLine> = emptyList(),
        val query: String = "",
        val matchCount: Int = 0,
        val currentOrdinal: Int = 0,
        val currentMatchLineId: Long? = null,
        val isPaused: Boolean = false,
        val pausedDropCount: Int = 0,
        val minPriority: Logging.Priority = Logging.Priority.DEBUG,
    )

    companion object {
        private val TAG = logTag("LogView", "Floating", "ViewModel")
        private const val SNAPSHOT_THROTTLE_MS = 250L
        private const val CLIP_LABEL = "SD Maid SE log"
    }
}
