package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.files.isFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.util.LinkedList

class IndirectLocalWalker(
    private val gateway: LocalGateway,
    private val mode: LocalGateway.Mode = LocalGateway.Mode.AUTO,
    private val start: LocalPath,
    private val onFilter: suspend (LocalPathLookup) -> Boolean = { true },
    private val onError: suspend (LocalPathLookup, Exception) -> Boolean = { _, _ -> true },
    private val followSymlinks: Boolean = false,
) : AbstractFlow<LocalPathLookup>() {
    private val tag = "$TAG#${hashCode()}"

    override suspend fun collectSafely(collector: FlowCollector<LocalPathLookup>) {
        // followSymlinks is not supported here: resolving a symlink's target type and canonical path
        // requires stat-ing paths the app process can't reach in ROOT/ADB mode, and the resolution
        // can't be delegated to the privileged host because this walker only exists to run the
        // app-side onFilter/onError callbacks (which can't cross the IPC boundary). Callback-free
        // follow-walks go host-side via DirectLocalWalker (see WalkOptions.isDirect). Here we only
        // descend real directories — no symlink following, so no traversal cycles are possible.
        if (followSymlinks) {
            log(TAG, WARN) { "followSymlinks is not supported for indirect ($mode) walks, nested symlinks won't be followed: $start" }
        }

        val startLookUp = gateway.lookup(start, mode)

        if (startLookUp.isFile) {
            collector.emit(startLookUp)
            return
        }

        val queue = LinkedList(listOf(startLookUp))

        while (!queue.isEmpty()) {
            val lookUp = queue.removeFirst()

            // Children are consumed as they stream in over the gateway, a huge directory no
            // longer stalls the whole walk until its complete listing has materialized.
            // The read-error handling is attached to the upstream flow only: exceptions from
            // onFilter, downstream operators or collectors, and cancellation (e.g. Flow.first)
            // must all propagate instead of being swallowed as read errors by onError.
            flow { emitAll(gateway.lookupFilesFlow(lookUp.lookedUp, mode)) }
                .catch { e ->
                    if (e !is Exception || e is CancellationException) throw e
                    log(TAG, ERROR) { "Failed to read $lookUp: $e" }
                    if (!onError(lookUp, e)) throw e
                }
                .collect { child ->
                    val allowed = onFilter(child)
                    if (!allowed) {
                        if (Bugs.isTrace) log(tag, VERBOSE) { "Skipping (filter): $child" }
                        return@collect
                    }
                    if (child.isDirectory) {
                        if (Bugs.isTrace) log(tag, VERBOSE) { "Walking: $child" }
                        queue.addFirst(child)
                    }
                    collector.emit(child)
                }
        }
    }

    companion object {
        private val TAG = logTag("Gateway", "Local", "Walker", "Indirect")
    }
}