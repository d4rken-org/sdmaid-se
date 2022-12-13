package eu.darken.sdmse.common.progress

import android.content.Context
import androidx.annotation.StringRes
import eu.darken.sdmse.R
import eu.darken.sdmse.common.castring.CaString
import eu.darken.sdmse.common.castring.toCaString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.EmptyCoroutineContext

fun <T : Progress.Client> T.updateProgressPrimary(primary: String) {
    updateProgress { (it ?: Progress.Data()).copy(primary = primary.toCaString()) }
}

fun <T : Progress.Client> T.updateProgressPrimary(primary: CaString) {
    updateProgress { (it ?: Progress.Data()).copy(primary = primary) }
}

fun <T : Progress.Client> T.updateProgressPrimary(resolv: (Context) -> String) {
    updateProgress { (it ?: Progress.Data()).copy(primary = resolv.toCaString()) }
}

fun <T : Progress.Client> T.updateProgressPrimary(@StringRes primary: Int, vararg args: Any) {
    updateProgress { (it ?: Progress.Data()).copy(primary = (primary to args).toCaString()) }
}

fun <T : Progress.Client> T.updateProgressSecondary(secondary: String) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = secondary.toCaString()) }
}

fun <T : Progress.Client> T.updateProgressSecondary(resolv: (Context) -> String) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = resolv.toCaString()) }
}

fun <T : Progress.Client> T.updateProgressSecondary(secondary: CaString) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = secondary) }
}

fun <T : Progress.Client> T.updateProgressSecondary(@StringRes secondary: Int, vararg args: Any) {
    updateProgress { (it ?: Progress.Data()).copy(secondary = (secondary to args).toCaString()) }
}

fun <T : Progress.Client> T.updateProgressCount(count: Progress.Count) {
    updateProgress { (it ?: Progress.Data()).copy(count = count) }
}

fun <T : Progress.Client> T.increaseProgress() {
    updateProgress { it?.copy(count = (it.count as Progress.Count.Counter).increment()) }
}

suspend fun <T : Progress.Host> T.forwardProgressTo(client: Progress.Client) = progress
    .onEach { pro -> client.updateProgress { pro } }
    .onCompletion { client.updateProgress { null } }

suspend fun <T : Progress.Host, R> T.withProgress(client: Progress.Client, action: suspend T.() -> R): R {
    val scope = CoroutineScope(EmptyCoroutineContext)

    val forwardingJob = forwardProgressTo(client).launchIn(scope)

    return try {
        action()
    } finally {
        forwardingJob.cancelAndJoin()
        scope.cancel("Finished scope")
    }
}