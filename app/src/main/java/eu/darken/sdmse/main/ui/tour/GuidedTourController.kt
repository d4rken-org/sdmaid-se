package eu.darken.sdmse.main.ui.tour

import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourSession
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.tour.TourPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuidedTourController @Inject constructor(
    private val generalSettings: GeneralSettings,
    @AppScope private val scope: CoroutineScope,
) {

    private val _session = MutableStateFlow<TourSession?>(null)
    val session: StateFlow<TourSession?> = _session.asStateFlow()

    @Volatile private var currentTopRoute: NavKey? = null
    @Volatile private var routeAtStart: NavKey? = null

    private val mutationMutex = Mutex()

    suspend fun shouldStart(definition: TourDefinition): Boolean {
        if (_session.value != null) return false
        val prefs = generalSettings.tourPreferences.value()
        val raw = definition.id.raw
        return raw !in prefs.completed && raw !in prefs.dismissed
    }

    suspend fun start(definition: TourDefinition) = mutationMutex.withLock {
        if (!shouldStart(definition)) {
            log(TAG, VERBOSE) { "start(${definition.id.raw}): blocked by prefs or active session" }
            return@withLock
        }
        routeAtStart = currentTopRoute
        val firstStep = definition.steps.firstOrNull()
        if (firstStep == null) {
            log(TAG) { "start(${definition.id.raw}): no steps, ignoring" }
            return@withLock
        }
        firstStep.prepareTarget?.invoke()
        log(TAG) { "start(${definition.id.raw})" }
        _session.value = TourSession(definition, stepIndex = 0)
    }

    suspend fun next() = mutationMutex.withLock {
        val s = _session.value ?: return@withLock
        if (s.isLast) {
            completeLocked()
            return@withLock
        }
        val nextStep = s.definition.steps[s.stepIndex + 1]
        nextStep.prepareTarget?.invoke()
        // Re-check session: prepareTarget may have suspended long enough for cancel/complete to fire.
        val still = _session.value ?: return@withLock
        if (still.definition.id != s.definition.id) return@withLock
        log(TAG, VERBOSE) { "next(${s.definition.id.raw}): ${s.stepIndex} -> ${s.stepIndex + 1}" }
        _session.value = still.copy(stepIndex = still.stepIndex + 1)
    }

    /** Go back one step. No-op when already at step 0. Doesn't re-run prepareTarget. */
    suspend fun previous() = mutationMutex.withLock {
        val s = _session.value ?: return@withLock
        if (s.stepIndex <= 0) return@withLock
        log(TAG, VERBOSE) { "previous(${s.definition.id.raw}): ${s.stepIndex} -> ${s.stepIndex - 1}" }
        _session.value = s.copy(stepIndex = s.stepIndex - 1)
    }

    /** Exit the current session without persisting anything. The tour will re-trigger next time. */
    suspend fun skipForNow() = mutationMutex.withLock {
        val s = _session.value ?: return@withLock
        log(TAG) { "skipForNow(${s.definition.id.raw})" }
        _session.value = null
        routeAtStart = null
    }

    /** Persistently dismiss the current tour. Won't show again until [reset] is called. */
    suspend fun dismissForever() = mutationMutex.withLock {
        val s = _session.value ?: return@withLock
        log(TAG) { "dismissForever(${s.definition.id.raw})" }
        persistDismissed(s.definition.id.raw)
        _session.value = null
        routeAtStart = null
    }

    suspend fun complete() = mutationMutex.withLock { completeLocked() }

    private suspend fun completeLocked() {
        val s = _session.value ?: return
        log(TAG) { "complete(${s.definition.id.raw})" }
        persistCompleted(s.definition.id.raw)
        _session.value = null
        routeAtStart = null
    }

    suspend fun reset() = mutationMutex.withLock {
        log(TAG) { "reset()" }
        generalSettings.tourPreferences.value(TourPreferences())
    }

    fun onRouteChanged(top: NavKey?) {
        currentTopRoute = top
        val s = _session.value ?: return
        if (s.definition.clickProtection) return
        if (top != routeAtStart) {
            log(TAG, VERBOSE) { "onRouteChanged: route changed during ${s.definition.id.raw}, completing" }
            scope.launch { complete() }
        }
    }

    private suspend fun persistDismissed(rawId: String) {
        generalSettings.tourPreferences.update { current ->
            current.copy(dismissed = current.dismissed + rawId)
        }
    }

    private suspend fun persistCompleted(rawId: String) {
        generalSettings.tourPreferences.update { current ->
            current.copy(completed = current.completed + rawId)
        }
    }

    companion object {
        private val TAG = logTag("GuidedTour", "Controller")
    }
}
