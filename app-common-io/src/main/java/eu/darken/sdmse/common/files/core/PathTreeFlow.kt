package eu.darken.sdmse.common.files.core

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import java.io.IOException
import java.util.*

// TODO support symlinks?
// TODO unit test coverage
class PathTreeFlow<
        P : APath,
        PL : APathLookup<P>,
        GT : APathGateway<P, PL>
        > constructor(
    private val gateway: GT,
    private val start: P,
    private val filter: (PL) -> Boolean = { true }
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
                log(TAG, ERROR) { "failed to read $lookUp: ${e.asLog()}" }
                emptyList()
            }

            newBatch
                .onEach { if (Bugs.isTrace) log(tag, VERBOSE) { "Walking: $it" } }
                .filter { filter(it) }
                .forEach { child ->
                    if (child.isDirectory) queue.addFirst(child)
                    collector.emit(child)
                }
        }
    }

    companion object {
        private val TAG = logTag("Gateway", "Walker")
    }
}