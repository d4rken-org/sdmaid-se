package eu.darken.sdmse.common.debug.logviewer.core

import eu.darken.sdmse.common.debug.logging.Logging
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.Instant
import javax.inject.Inject

class LogViewLogger @Inject constructor() : Logging.Logger {

    data class Item(
        val time: Instant,
        val priority: Logging.Priority,
        val message: String,
    )

    val lines = MutableSharedFlow<Item>(replay = 0, extraBufferCapacity = 50, BufferOverflow.DROP_OLDEST)

    override fun isLoggable(priority: Logging.Priority): Boolean {
        return priority != Logging.Priority.VERBOSE
    }

    override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
        lines.tryEmit(Item(Instant.now(), priority, message))
    }
}
