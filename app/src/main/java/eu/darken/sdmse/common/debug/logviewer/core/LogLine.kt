package eu.darken.sdmse.common.debug.logviewer.core

import eu.darken.sdmse.common.debug.logging.Logging

/**
 * A single, physical log row held in the [LogHistoryRecorder] ring buffer.
 *
 * Multiline messages (e.g. stack traces from `Throwable.asLog()`) are split into one [LogLine] per
 * line, so the ring-buffer cap and the on-screen scroll/anchor behave honestly (one trace ≠ one row).
 *
 * [id] is monotonic across the recorder's lifetime and used as a stable LazyColumn key, so that
 * front-drops from the ring buffer keep the viewport anchored on the line being read.
 */
data class LogLine(
    val id: Long,
    val priority: Logging.Priority,
    val tag: String,
    val message: String,
) {
    /** Single-line rendering used for copy/share. */
    fun render(): String = "${priority.shortLabel} $tag: $message"
}
