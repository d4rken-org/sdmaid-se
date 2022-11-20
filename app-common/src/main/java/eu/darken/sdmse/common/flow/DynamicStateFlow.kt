package eu.darken.sdmse.common.flow

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/**
 * A thread safe stateful flow that can be updated blocking and async with a lazy initial value provider.
 *
 * @param loggingTag will be prepended to logging tag, i.e. "$loggingTag:HD"
 * @param parentScope on which the update operations and callbacks will be executed on
 * @param coroutineContext used in combination with [CoroutineScope]
 * @param startValueProvider provides the first value, errors will be rethrown on [CoroutineScope]
 */
class DynamicStateFlow<T>(
    loggingTag: String? = null,
    parentScope: CoroutineScope,
    coroutineContext: CoroutineContext = parentScope.coroutineContext,
    private val onRelease: CoroutineScope.(T) -> Unit = {},
    private val startValueProvider: suspend CoroutineScope.() -> T,
) {
    private val lTag = loggingTag?.let { "$it:DSFlow" }

    private val updateActions = MutableSharedFlow<Update<T>>(
        replay = Int.MAX_VALUE,
        extraBufferCapacity = Int.MAX_VALUE,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    private val valueGuard = Mutex()

    private val producer: Flow<State<T>> = channelFlow {
        var currentValue = valueGuard.withLock {
            lTag?.let { log(it, VERBOSE) { "Providing startValue..." } }

            startValueProvider().also { startValue ->
                val initializer = Update<T>(onError = null, onModify = { startValue })
                send(State(value = startValue, updatedBy = initializer))
                lTag?.let { log(it, VERBOSE) { "...startValue provided and emitted." } }
            }
        }

        invokeOnClose {
            lTag?.let { log(it, VERBOSE) { "invokeOnClose executing..." } }
            onRelease(currentValue)
            lTag?.let { log(it, VERBOSE) { "internal channelFlow finished." } }
        }

        updateActions.collect { update ->
            currentValue = valueGuard.withLock {
                try {
                    update.onModify(currentValue).also {
                        send(State(value = it, updatedBy = update))
                    }
                } catch (e: Exception) {
                    lTag?.let {
                        log(it, VERBOSE) { "Data modifying failed (onError=${update.onError}): ${e.asLog()}" }
                    }

                    if (update.onError != null) {
                        update.onError.invoke(e)
                    } else {
                        send(State(value = currentValue, error = e, updatedBy = update))
                    }

                    currentValue
                }
            }
        }
    }

    private val internalFlow = producer
        .onStart { lTag?.let { log(it, VERBOSE) { "Internal onStart" } } }
//        .onEach { value -> lTag?.let { log(it, VERBOSE) { "New value: $value" } } }
        .onCompletion { err ->
            when {
                err is CancellationException -> {
                    lTag?.let { log(it, VERBOSE) { "internal onCompletion() due to cancellation" } }
                }
                err != null -> {
                    lTag?.let { log(it, VERBOSE) { "internal onCompletion() due to error: ${err.asLog()}" } }
                }
                else -> {
                    lTag?.let { log(it, VERBOSE) { "internal onCompletion()" } }
                }
            }
        }
        .shareIn(
            scope = parentScope + coroutineContext,
            replay = 1,
            started = SharingStarted.Lazily
        )

    val flow: Flow<T> = internalFlow
        .map { it.value }
        .distinctUntilChanged()

    suspend fun value() = flow.first()

    /**
     * Non blocking update method.
     * Gets executed on the scope and context this instance was initialized with.
     *
     * @param onError if you don't provide this, and exception in [onUpdate] will the scope passed to this class
     */
    fun updateAsync(
        onError: (suspend (Exception) -> Unit) = { throw it },
        onUpdate: suspend T.() -> T,
    ) {
        val update: Update<T> = Update(
            onModify = onUpdate,
            onError = onError
        )
        runBlocking { updateActions.emit(update) }
    }

    /**
     * Blocking update method
     * Gets executed on the scope and context this instance was initialized with.
     * Waiting will happen on the callers scope.
     *
     * Any errors that occurred during [action] will be rethrown by this method.
     */
    suspend fun updateBlocking(action: suspend T.() -> T): T {
        val update: Update<T> = Update(onModify = action)
        updateActions.emit(update)

        lTag?.let { log(it, VERBOSE) { "Waiting for update." } }
        val ourUpdate = internalFlow.first { it.updatedBy == update }
        lTag?.let { log(it, VERBOSE) { "Finished waiting, got $ourUpdate" } }

        ourUpdate.error?.let { throw it }

        return ourUpdate.value
    }

    private data class Update<T>(
        val onModify: suspend T.() -> T,
        val onError: (suspend (Exception) -> Unit)? = null,
    )

    private data class State<T>(
        val value: T,
        val error: Exception? = null,
        val updatedBy: Update<T>,
    )
}
