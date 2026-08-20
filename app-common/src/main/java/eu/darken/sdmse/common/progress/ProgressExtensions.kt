package eu.darken.sdmse.common.progress

import android.content.Context
import androidx.annotation.StringRes
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlin.coroutines.EmptyCoroutineContext

fun <T : Progress.Client> T.updateProgressPrimary(primary: String) {
    updateProgress { (it ?: Progress.Data()).copy(primary = primary.toCaString()) }
}

fun <T : Progress.Client> T.updateProgressPrimary(primary: CaString) {
    updateProgress { (it ?: Progress.Data()).copy(primary = primary) }
}

fun <T : Progress.Client> T.updateProgressPrimary(resolv: (Context) -> String) {
    updateProgress { (it ?: Progress.Data()).copy(primary = caString { resolv(this) }) }
}

fun <T : Progress.Client> T.updateProgressPrimary(@StringRes primary: Int, vararg args: Any) {
    updateProgress { (it ?: Progress.Data()).copy(primary = (primary to args).toCaString()) }
}

fun <T : Progress.Client> T.updateProgressSecondary(secondary: String) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = secondary.toCaString(), extra = null) }
}

fun <T : Progress.Client> T.updateProgressSecondary(resolv: (Context) -> String) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = caString { resolv(this) }, extra = null) }
}

fun <T : Progress.Client> T.updateProgressSecondary(secondary: CaString = CaString.EMPTY) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = secondary, extra = null) }
}

fun <T : Progress.Client> T.updateProgressSecondary(@StringRes secondary: Int, vararg args: Any) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = (secondary to args).toCaString(), extra = null) }
}

/** Sets the item label and its payload in one update, so the UI can never pair a new label with a stale payload. */
fun <T : Progress.Client> T.updateProgressSecondary(secondary: CaString, extra: Any?) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = secondary, extra = extra) }
}

fun <T : Progress.Client> T.updateProgressSecondary(secondary: String, extra: Any?) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = secondary.toCaString(), extra = extra) }
}

fun <T : Progress.Client> T.updateProgressCount(count: Progress.Count) {
    updateProgress { (it ?: Progress.Data()).copy(count = count) }
}

fun <T : Progress.Client> T.updateProgressSubCount(subCount: Progress.Count?) {
    updateProgress { (it ?: Progress.Data()).copy(subCount = subCount) }
}

/** Fraction for a determinate ring, or null when the ring should spin. */
fun Progress.Count?.determinateFraction(): Float? = when (this) {
    is Progress.Count.Counter,
    is Progress.Count.Percent,
    -> if (max > 0L) (current.toFloat() / max.toFloat()).coerceIn(0f, 1f) else null

    else -> null
}

fun <T : Progress.Client> T.increaseProgress(value: Int = 1) {
    updateProgress {
        when (it?.count) {
            is Progress.Count.Counter -> it.copy(count = (it.count as Progress.Count.Counter).increment(value))
            is Progress.Count.Percent -> it.copy(count = (it.count as Progress.Count.Percent).increment(value))
            else -> {
                log(VERBOSE) { "Can't increaseProgress() on type: ${it?.count}" }
                it
            }
        }
    }
}

suspend fun <T : Progress.Host> T.forwardProgressTo(
    client: Progress.Client,
    onUpdate: (existing: Progress.Data?, new: Progress.Data?) -> Progress.Data?,
    onCompletion: (Progress.Data?) -> Progress.Data?,
) = progress
    .onEach { new -> client.updateProgress { onUpdate(it, new) } }
    .onCompletion { if (currentCoroutineContext().isActive) client.updateProgress { onCompletion(it) } }

suspend fun <T : Progress.Host, R> T.withProgress(
    client: Progress.Client,
    onUpdate: (existing: Progress.Data?, new: Progress.Data?) -> Progress.Data? = { _, new -> new },
    onCompletion: (Progress.Data?) -> Progress.Data? = { Progress.Data() },
    action: suspend T.() -> R
): R {
    val scope = CoroutineScope(EmptyCoroutineContext)

    val forwardingJob = forwardProgressTo(
        client,
        onUpdate,
        onCompletion
    ).launchIn(scope)

    return try {
        action()
    } finally {
        forwardingJob.cancelAndJoin()
        scope.cancel("Finished scope")
        // Flow's onCompletion doesn't restore because isActive is false after cancellation
        client.updateProgress { onCompletion(it) }
    }
}