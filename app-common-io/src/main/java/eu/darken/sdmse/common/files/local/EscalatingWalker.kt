package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.files.core.local.listFiles2
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.files.isFile
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import java.io.IOException
import java.util.LinkedList

// TODO support symlinks?
// TODO unit test coerage
class EscalatingWalker constructor(
    private val gateway: LocalGateway,
    private val start: LocalPath,
    private val onFilter: suspend (LocalPathLookup) -> Boolean = { true },
    private val onError: suspend (LocalPathLookup, Exception) -> Boolean = { _, _ -> true }
) : AbstractFlow<LocalPathLookup>() {
    private val tag = "$TAG#${hashCode()}"

    override suspend fun collectSafely(collector: FlowCollector<LocalPathLookup>) {
        val startLookUp = gateway.lookup(start)

        if (startLookUp.isFile) {
            collector.emit(startLookUp)
            return
        }

        val escalationMode = when {
            gateway.hasRoot() -> LocalGateway.Mode.ROOT
            gateway.hasShizuku() -> LocalGateway.Mode.ADB
            else -> null
        }

        val queue = LinkedList(listOf(startLookUp))

        suspend fun Collection<LocalPathLookup>.process() = this
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

        while (!queue.isEmpty()) {
            val lookUp = queue.removeFirst()
            var blockingError: Exception? = null

            try {
                lookUp.lookedUp.asFile()
                    .listFiles2()
                    .map { it.toLocalPath().performLookup() }
                    .process()
                continue
            } catch (e: IOException) {
                blockingError = e
            }

            if (escalationMode != null) {
                log(TAG, VERBOSE) { "Escalating to $escalationMode for $lookUp" }
                try {
                    gateway
                        .lookupFiles(lookUp.lookedUp, escalationMode)
                        .process()
                    continue
                } catch (e: IOException) {
                    log(TAG, DEBUG) { "Failed to read despite escalation ($escalationMode) $lookUp: $e" }
                    blockingError = e
                }
            }

            if (blockingError != null) {
                log(TAG, WARN) { "Failed to read $lookUp: $blockingError" }
                if (onError(lookUp, blockingError)) {
                    continue
                } else {
                    throw blockingError
                }
            }
        }
    }

    companion object {
        private val TAG = logTag("Gateway", "Local", "Walker", "Escalating")
    }
}