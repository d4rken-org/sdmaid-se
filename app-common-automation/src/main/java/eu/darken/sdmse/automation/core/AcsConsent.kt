package eu.darken.sdmse.automation.core

import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn

/**
 * Caches the ACS consent setting so the accessibility service never has to read DataStore
 * from the main thread (that blocking read showed up as ANRs in Play vitals).
 */
class AcsConsent(
    source: Flow<Boolean?>,
    scope: CoroutineScope,
) {

    /** The stored consent can legitimately be `null`, so "not loaded yet" needs its own wrapper level. */
    data class Cached(val consent: Boolean?)

    private val cache: StateFlow<Cached?> = source
        .map { Cached(it) }
        .retry {
            log(TAG, ERROR) { "Consent read failed: ${it.asLog()}" }
            delay(1000)
            true
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Non-suspending, for the main-thread event hot path. null = not loaded yet OR stored null, both fail closed. */
    val current: Boolean?
        get() = cache.value?.consent

    /** Suspends until the first real emission, so this can tell "not loaded" apart from "stored null". */
    suspend fun await(): Boolean? = cache.filterNotNull().first().consent

    companion object {
        private val TAG = logTag("Automation", "Service", "AcsConsent")
    }
}
