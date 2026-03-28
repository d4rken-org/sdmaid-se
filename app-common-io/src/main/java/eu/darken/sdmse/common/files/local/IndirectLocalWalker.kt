package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.files.isFile
import eu.darken.sdmse.common.files.isSymlink
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
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
        val startLookUp = gateway.lookup(start, mode)

        if (startLookUp.isFile) {
            collector.emit(startLookUp)
            return
        }

        val queue = LinkedList(listOf(startLookUp))
        val visitedCanonical = if (followSymlinks) HashSet<String>() else null

        while (!queue.isEmpty()) {
            val lookUp = queue.removeFirst()

            val newBatch = try {
                gateway.lookupFiles(lookUp.lookedUp, mode)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to read $lookUp: $e" }
                if (onError(lookUp, e)) {
                    emptyList()
                } else {
                    throw e
                }
            }

            newBatch
                .filter {
                    val allowed = onFilter(it)
                    if (Bugs.isTrace) {
                        if (!allowed) log(tag, VERBOSE) { "Skipping (filter): $it" }
                    }
                    allowed
                }
                .forEach { child ->
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
        private val TAG = logTag("Gateway", "Local", "Walker", "Indirect")
    }
}