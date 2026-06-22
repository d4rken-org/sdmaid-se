package eu.darken.sdmse.common.debug.logviewer.core

import eu.darken.sdmse.common.debug.logging.Logging
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory backlog for the floating log panel.
 *
 * A [Logging.Logger] backed by a bounded ring buffer (last [BUFFER_CAP] physical lines, drop-oldest).
 * Because [Logging.Logger.log] is synchronous and cannot suspend, this class does the cheapest
 * possible work on the logging thread — a locked deque append plus a conflated change signal — and
 * leaves throttling/snapshotting to the collector (the ViewModel).
 *
 * Installation is ref-counted via [acquire]/[release]: the global logger is added on the first active
 * owner and removed only after the last one leaves, so two panel owners (e.g. two Activity instances)
 * can't tear each other's capture down. The buffer is retained across release/acquire within a
 * process, so toggling the panel keeps scrollback.
 *
 * [setPaused] freezes the buffer for inspection: while paused, [log] neither appends nor drops, so the
 * line under inspection can't age out of the window. The separate `LogCatLogger` keeps writing to
 * logcat, so nothing is lost system-wide.
 */
@Singleton
class LogHistoryRecorder @Inject constructor() : Logging.Logger {

    private val lock = Any()
    private val buffer = ArrayDeque<LogLine>(BUFFER_CAP)
    private var idCounter = 0L
    private var pausedDrops = 0
    private var activeOwners = 0

    @Volatile private var pausedFlag = false
    val isPaused: Boolean get() = pausedFlag

    /** Minimum priority captured into the buffer. Defaults to DEBUG (VERBOSE excluded), as before. */
    @Volatile var minPriority: Logging.Priority = Logging.Priority.DEBUG

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Conflated signal fired whenever the buffer (or the paused-drop counter) changes. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    override fun isLoggable(priority: Logging.Priority): Boolean = priority.intValue >= minPriority.intValue

    override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
        synchronized(lock) {
            if (pausedFlag) {
                // Frozen for inspection: don't touch the buffer, just count what we skipped.
                pausedDrops += message.lineCount()
            } else {
                // Split multiline messages (stack traces) so the cap and scrolling stay honest.
                for (line in message.splitToSequence('\n')) {
                    buffer.addLast(LogLine(id = idCounter++, priority = priority, tag = tag, message = line))
                    while (buffer.size > BUFFER_CAP) buffer.removeFirst()
                }
            }
        }
        _changes.tryEmit(Unit)
    }

    /** Immutable snapshot of the current buffer contents. */
    fun snapshot(): List<LogLine> = synchronized(lock) { buffer.toList() }

    /** A consistent read of buffer + paused state, taken under a single lock for the throttled UI snapshot. */
    fun read(): Reading = synchronized(lock) { Reading(buffer.toList(), pausedFlag, pausedDrops) }

    fun setPaused(value: Boolean) {
        synchronized(lock) {
            pausedFlag = value
            pausedDrops = 0
        }
        _changes.tryEmit(Unit)
    }

    /** Drop all buffered lines. Ids keep advancing so they stay monotonic across a clear. */
    fun clear() {
        synchronized(lock) {
            buffer.clear()
            pausedDrops = 0
        }
        _changes.tryEmit(Unit)
    }

    /** Register an active owner; installs the global logger on the first one. */
    fun acquire() {
        val install = synchronized(lock) { activeOwners++ == 0 }
        if (install) Logging.install(this)
    }

    /** Release an active owner; removes the global logger (and clears the freeze) after the last one. */
    fun release() {
        val remove = synchronized(lock) {
            when {
                activeOwners == 0 -> false
                --activeOwners == 0 -> {
                    pausedFlag = false
                    pausedDrops = 0
                    true
                }
                else -> false
            }
        }
        if (remove) Logging.remove(this)
    }

    data class Reading(
        val lines: List<LogLine>,
        val isPaused: Boolean,
        val droppedWhilePaused: Int,
    )

    companion object {
        const val BUFFER_CAP = 8000
        private fun String.lineCount(): Int = 1 + count { it == '\n' }
    }
}
