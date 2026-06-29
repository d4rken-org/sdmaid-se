package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.files.core.local.listFilesStreaming
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.files.isFile
import eu.darken.sdmse.common.files.isSymlink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import java.util.LinkedList

class DirectLocalWalker(
    private val start: LocalPath,
    private val onFilter: suspend (LocalPathLookup) -> Boolean = { true },
    private val onError: suspend (LocalPathLookup, Exception) -> Boolean = { _, _ -> true },
    private val followSymlinks: Boolean = false,
) : AbstractFlow<LocalPathLookup>() {
    private val tag = "$TAG#${hashCode()}"

    override suspend fun collectSafely(collector: FlowCollector<LocalPathLookup>) {
        val startLookUp = start.performLookup()
        if (startLookUp.isFile) {
            collector.emit(startLookUp)
            return
        }

        val queue = LinkedList(mutableListOf(startLookUp))
        // Seed with the start dir's canonical path so a child symlink pointing back to start
        // doesn't re-walk the root subtree once before its descendants get deduped.
        val visitedCanonical = if (followSymlinks) {
            HashSet<String>().apply { runCatching { add(start.asFile().canonicalPath) } }
        } else {
            null
        }

        while (!queue.isEmpty()) {
            val lookUp = queue.removeFirst()

            // Enumerate each directory lazily (NIO) so a single huge directory isn't fully
            // materialized. Listing + per-entry lookup errors route to onError (as before); a filter
            // or emit error (incl. cancellation) propagates. Note: unlike the old eager listing — and
            // unlike IndirectLocalWalker, which stays eager/all-or-nothing per directory — an error
            // part-way through a directory keeps the entries already emitted before it.
            lookUp.lookedUp.asFile().listFilesStreaming()
                .map { it.toLocalPath().performLookup() }
                .catch { e ->
                    if (e is CancellationException) throw e
                    val error = e as? Exception ?: throw e
                    log(TAG, ERROR) { "Failed to read $lookUp: $error" }
                    if (!onError(lookUp, error)) throw error
                }
                .collect { child ->
                    val allowed = onFilter(child)
                    if (Bugs.isTrace && !allowed) log(tag, VERBOSE) { "Skipping (filter): $child" }
                    if (!allowed) return@collect

                    val shouldDescend = child.isDirectory ||
                        (followSymlinks && child.isSymlink && child.lookedUp.asFile().isDirectory)

                    if (shouldDescend) {
                        if (visitedCanonical != null) {
                            val canonical = try {
                                child.lookedUp.asFile().canonicalPath
                            } catch (e: Exception) {
                                null
                            }
                            if (canonical != null && !visitedCanonical.add(canonical)) {
                                log(tag, WARN) { "Already visited, skipping: $child -> $canonical" }
                            } else {
                                if (Bugs.isTrace) log(tag, VERBOSE) { "Walking: $child" }
                                queue.addFirst(child)
                            }
                        } else {
                            if (Bugs.isTrace) log(tag, VERBOSE) { "Walking: $child" }
                            queue.addFirst(child)
                        }
                    }
                    collector.emit(child)
                }
        }
    }

    companion object {
        private val TAG = logTag("Gateway", "Local", "Walker", "Direct")
    }
}