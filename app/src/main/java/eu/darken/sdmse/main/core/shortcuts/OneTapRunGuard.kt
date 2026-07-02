package eu.darken.sdmse.main.core.shortcuts

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-flight guard for the OneTap "scan & delete" run started from the widget / launcher shortcut.
 *
 * [ShortcutActivity][eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity] submits the enabled one-click
 * tools sequentially, so the TaskManager state momentarily reports idle in the gap between tools. That
 * gap is both a spam hole (a second tap could start another run) and a flicker source (the widget's
 * Clean button would briefly reappear mid-run). This guard spans the whole sequence:
 *
 * - [tryStart] atomically claims the run and remembers its [Job]; a losing caller should just open
 *   the app instead of submitting again.
 * - [running] lets the widget render a stable "working" state for the entire run.
 * - [cancelRun] cancels the run's job, so tools that haven't been submitted yet never start —
 *   cancelling the in-flight task alone would let the sequence march on to the next tool.
 * - [finish] must be called in a `finally` so the flag is always released.
 *
 * App-scoped singleton; recreated on process restart, so a killed run cannot get stuck.
 */
@Singleton
class OneTapRunGuard @Inject constructor() {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var runJob: Job? = null

    /**
     * Atomically claim the run and register its [job]. Returns true if it was idle (caller should
     * proceed), false if a run is already active.
     */
    @Synchronized
    fun tryStart(job: Job): Boolean {
        if (_running.value) return false
        runJob = job
        _running.value = true
        return true
    }

    /** Cancel the active run (pending tool submits won't start). No-op when idle. */
    @Synchronized
    fun cancelRun() {
        runJob?.cancel()
    }

    @Synchronized
    fun finish() {
        runJob = null
        _running.value = false
    }
}
