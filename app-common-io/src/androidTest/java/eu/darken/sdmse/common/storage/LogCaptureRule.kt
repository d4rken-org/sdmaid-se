package eu.darken.sdmse.common.storage

import android.util.Log
import eu.darken.sdmse.common.debug.logging.Logging
import org.junit.rules.ExternalResource

/**
 * [Logging] starts with no receivers, so the wrappers' own diagnostics are dropped unless a test installs one.
 * The wrappers log instead of throwing, which makes those lines the only evidence of which branch they took.
 */
class LogCaptureRule : ExternalResource() {

    private val lines = mutableListOf<String>()

    private val logger = object : Logging.Logger {
        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            synchronized(lines) { lines.add("${priority.shortLabel}/$tag $message") }
        }
    }

    val captured: List<String>
        get() = synchronized(lines) { lines.toList() }

    override fun before() = Logging.install(logger)

    override fun after() {
        captured.forEach { Log.i("SDMSEPROBE", "captured: $it") }
        Logging.remove(logger)
    }
}
