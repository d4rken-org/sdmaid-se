package eu.darken.sdmse.common.files

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import java.io.IOException
import java.util.LinkedList

// TODO support symlinks?
// TODO unit test coverage
class PathTreeFlow<
        P : APath,
        PL : APathLookup<P>,
        PLE : APathLookupExtended<P>,
        GT : APathGateway<P, PL, PLE>
        > constructor(
    private val gateway: GT,
    private val start: P,
    private val onFilter: suspend (PL) -> Boolean = { true },
    private val onError: suspend (PL, Exception) -> Boolean = { _, _ -> true }
) : AbstractFlow<PL>() {
    private val tag = "$TAG#${hashCode()}"
    override suspend fun collectSafely(collector: FlowCollector<PL>) {
        val startLookUp = start.lookup(gateway)
        if (startLookUp.isFile) {
            collector.emit(startLookUp)
            return
        }

        val queue = LinkedList(listOf(startLookUp))

        while (!queue.isEmpty()) {

            val lookUp = queue.removeFirst()

            val newBatch = try {
                lookUp.lookedUp.lookupFiles(gateway)
            } catch (e: IOException) {
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
                    if (child.isDirectory) {
                        if (Bugs.isTrace) log(tag, VERBOSE) { "Walking: $child" }
                        queue.addFirst(child)
                    }
                    collector.emit(child)
                }
        }
    }

    companion object {
        private val TAG = logTag("Gateway", "Walker")
    }
}