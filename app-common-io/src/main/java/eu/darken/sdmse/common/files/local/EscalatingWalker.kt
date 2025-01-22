package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.files.core.local.listFiles2
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.files.isFile
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import java.util.LinkedList

// TODO support symlinks?
// TODO unit test coerage
/**
 * Prevents unnecessary lookups in Mode.NORMAL for nested directories
 */
class EscalatingWalker(
    private val gateway: LocalGateway,
    private val start: LocalPath,
    private val options: APathGateway.WalkOptions<LocalPath, LocalPathLookup> = APathGateway.WalkOptions()
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
            gateway.hasAdb() -> LocalGateway.Mode.ADB
            else -> null
        }

        val queue = LinkedList<QueuedItem>().apply {
            add(QueuedItem(startLookUp, LocalGateway.Mode.NORMAL))
        }

        while (!queue.isEmpty()) {
            val item = queue.removeFirst()

            when {
                item.targetMode == LocalGateway.Mode.NORMAL -> {
                    try {
                        item.target.lookedUp.asFile()
                            .listFiles2()
                            .map { it.toLocalPath().performLookup() }
                            .filter {
                                val allowed = options.onFilter?.invoke(it) ?: true
                                if (Bugs.isTrace && !allowed) {
                                    log(tag, VERBOSE) { "Skipping (filter): $it" }
                                }
                                allowed
                            }
                            .forEach { child ->
                                if (child.isDirectory) {
                                    if (Bugs.isTrace) log(tag, VERBOSE) { "Walking: $child" }
                                    queue.addFirst(item.toSubItem(child))
                                }
                                collector.emit(child)
                            }
                        continue
                    } catch (e: Exception) {
                        log(TAG, VERBOSE) { "Escalating ${item.target.lookedUp} to $escalationMode due to: $e" }
                        queue.addFirst(item.copy(targetMode = escalationMode, error = e))
                    }
                }

                item.targetMode == LocalGateway.Mode.ROOT || item.targetMode == LocalGateway.Mode.ADB -> {
                    try {
                        gateway
                            .walk(
                                path = item.target.lookedUp,
                                options = options,
                                mode = item.targetMode
                            )
                            .collect { child ->
                                // `walk` already processes all subdirectories, no need to queue them again
                                collector.emit(child)
                            }
                        continue
                    } catch (e: Exception) {
                        log(TAG, DEBUG) { "Failed to read despite escalation: ${item.target.lookedUp}: $e" }
                        queue.addFirst(item.copy(targetMode = null, error = e))
                    }
                }

                item.error != null -> {
                    log(TAG, WARN) { "Failed to read ${item.target}: ${item.error}" }
                    if (options.onError?.invoke(item.target, item.error) != false) {
                        continue
                    } else {
                        throw item.error
                    }
                }
            }
        }
    }

    data class QueuedItem(
        val target: LocalPathLookup,
        val targetMode: LocalGateway.Mode? = LocalGateway.Mode.NORMAL,
        val error: Exception? = null,
    ) {
        fun toSubItem(target: LocalPathLookup) = copy(
            target = target,
            error = null,
        )
    }

    companion object {
        private val TAG = logTag("Gateway", "Local", "Walker", "Escalating")
    }
}