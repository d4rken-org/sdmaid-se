package eu.darken.sdmse.common.results

import eu.darken.sdmse.common.flow.SingleEventFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-screen result delivery, replacing Fragment result APIs.
 * Each result is keyed by a string identifier and delivered via [SingleEventFlow]
 * so it is consumed exactly once.
 */
@Singleton
class ResultBus @Inject constructor() {
    private val channels = mutableMapOf<String, SingleEventFlow<Any>>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getFlow(key: String): SingleEventFlow<T> = synchronized(channels) {
        channels.getOrPut(key) { SingleEventFlow() } as SingleEventFlow<T>
    }

    suspend fun <T : Any> emit(key: String, value: T) {
        getFlow<T>(key).emit(value)
    }

    fun <T : Any> tryEmit(key: String, value: T) {
        getFlow<T>(key).tryEmit(value)
    }
}
