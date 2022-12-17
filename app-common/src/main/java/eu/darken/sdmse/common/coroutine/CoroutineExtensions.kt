package eu.darken.sdmse.common.coroutine

import kotlinx.coroutines.Job
import java.io.Closeable


suspend fun <T> Job.cancelAfterRun(action: suspend () -> T): T = try {
    action()
} finally {
    cancel()
}

suspend fun <T : Closeable, R> T.use(block: suspend T.() -> R): R = try {
    block(this)
} finally {
    // TODO ?
    close()
}