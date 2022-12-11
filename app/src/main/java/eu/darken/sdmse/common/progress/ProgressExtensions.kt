package eu.darken.sdmse.common.progress

import android.content.Context
import androidx.annotation.StringRes
import eu.darken.sdmse.common.castring.CaString
import eu.darken.sdmse.common.castring.toCaString
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

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
    .onCompletion { client.updateProgress { null } }
    .onEach { pro -> client.updateProgress { pro } }
