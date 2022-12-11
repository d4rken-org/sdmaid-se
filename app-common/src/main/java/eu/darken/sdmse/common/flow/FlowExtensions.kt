package eu.darken.sdmse.common.flow

import eu.darken.sdmse.common.coroutine.cancelAfterRun
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.error.hasCause
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlin.time.Duration

/**
 * Create a stateful flow, with the initial value of null, but never emits a null value.
 * Helper method to create a new flow without suspending and without initial value
 * The flow collector will just wait for the first value
 */
fun <T : Any> Flow<T>.shareLatest(
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
    tag: String? = null,
) = this
    .onStart { if (tag != null) log(tag, VERBOSE) { "shareLatest(...) start" } }
    .onEach { if (tag != null) log(tag, VERBOSE) { "shareLatest(...) emission: $it" } }
    .onCompletion { if (tag != null) log(tag, VERBOSE) { "shareLatest(...) completed." } }
    .catch {
        if (tag != null) log(tag, VERBOSE) { "shareLatest(...) catch(): ${it.asLog()}" }
        throw it
    }
    .stateIn(
        scope = scope,
        started = started,
        initialValue = null
    )
    .filterNotNull()

fun <T : Any?> Flow<T>.replayingShare(scope: CoroutineScope) = this.shareIn(
    scope = scope,
    replay = 1,
    started = SharingStarted.WhileSubscribed(replayExpiration = Duration.ZERO)
)

fun <T> Flow<T>.withPrevious(): Flow<Pair<T?, T>> = this
    .scan(Pair<T?, T?>(null, null)) { previous, current -> Pair(previous.second, current) }
    .drop(1)
    .map {
        @Suppress("UNCHECKED_CAST")
        it as Pair<T?, T>
    }


fun <T> Flow<T>.onError(block: suspend (Throwable) -> Unit) = this.catch {
    block(it)
    throw it
}

fun <T> Flow<T>.takeUntilAfter(predicate: suspend (T) -> Boolean) = transformWhile {
    val fullfilled = predicate(it)
    emit(it)
    !fullfilled // We keep emitting until condition is fullfilled = true
}

fun <T> Flow<T>.setupCommonEventHandlers(tag: String, identifier: () -> String) = this
    .onStart { log(tag, VERBOSE) { "${identifier()}.onStart()" } }
    .onEach { log(tag, VERBOSE) { "${identifier()}.onEach(): $it" } }
    .onCompletion { log(tag, VERBOSE) { "${identifier()}.onCompletion()" } }
    .catch {
        if (it.hasCause(CancellationException::class)) {
            log(tag, VERBOSE) { "${identifier()} cancelled" }
        } else {
            log(tag, ERROR) { "${identifier()} failed: ${it.asLog()}" }
            throw it
        }
    }

suspend fun <T> Flow<*>.launchForAction(scope: CoroutineScope, action: suspend () -> T): T = this
    .launchIn(scope).cancelAfterRun(action)