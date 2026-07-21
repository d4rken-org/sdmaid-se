package eu.darken.sdmse.common.compose.tour

import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuidedTourController @Inject constructor(
    private val generalSettings: GeneralSettings,
    @AppScope private val scope: CoroutineScope,
) : GuidedTourAccess {

    private val _session = MutableStateFlow<TourSession?>(null)
    override val session: StateFlow<TourSession?> = _session.asStateFlow()

    @Volatile private var currentTopRoute: NavKey? = null
    @Volatile private var routeAtStart: NavKey? = null

    // Whether the active session has had at least one step actually rendered (anchored or
    // centerless). Reset on start, flipped by the host via [markStepRendered]. Guards against
    // persisting "completed" for a tour that fell through entirely on missing-target grace-skips.
    @Volatile private var sessionStepRendered: Boolean = false

    // Singleton-scoped, so this resets on app restart — which is exactly what "skip for now" means:
    // suppress the tour for the current process lifetime, not persistently.
    private val skippedThisSession: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val mutationMutex = Mutex()

    override suspend fun shouldStart(definition: TourDefinition): Boolean {
        if (!generalSettings.isGuidedToursEnabled.value()) return false
        if (_session.value != null) return false
        val raw = definition.id.raw
        if (raw in skippedThisSession) return false
        val prefs = generalSettings.tourPreferences.value()
        return raw !in prefs.completed && raw !in prefs.dismissed
    }

    override suspend fun start(definition: TourDefinition) = mutationMutex.withLock {
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
        sessionStepRendered = false
        firstStep.prepareTarget?.invoke()
        log(TAG) { "start(${definition.id.raw})" }
        _session.value = TourSession(definition, stepIndex = 0)
    }

    suspend fun next() {
        val onComplete: (suspend () -> Unit)? = mutationMutex.withLock {
            val s = _session.value ?: return@withLock null
            if (s.isLast) return@withLock completeLocked()

            val nextStep = s.definition.steps[s.stepIndex + 1]
            nextStep.prepareTarget?.invoke()
            // Re-check session: prepareTarget may have suspended long enough for cancel/complete to fire.
            val still = _session.value ?: return@withLock null
            if (still.definition.id != s.definition.id) return@withLock null
            log(TAG, VERBOSE) { "next(${s.definition.id.raw}): ${s.stepIndex} -> ${s.stepIndex + 1}" }
            _session.value = still.copy(stepIndex = still.stepIndex + 1)
            null
        }
        // Screen callbacks can animate or acquire their own locks, so never run them while holding
        // the controller mutation lock. completeLocked() has already persisted and cleared state.
        onComplete?.invoke()
    }

    /** Go back one step. No-op when already at step 0. Re-runs the destination step's prepareTarget. */
    suspend fun previous() = mutationMutex.withLock {
        val s = _session.value ?: return@withLock
        if (s.stepIndex <= 0) return@withLock
        val previousStep = s.definition.steps[s.stepIndex - 1]
        // Mirror next(): give the destination step a chance to scroll/expand its target
        // before we publish the new index, so the host doesn't grace-skip an off-screen target.
        previousStep.prepareTarget?.invoke()
        val still = _session.value ?: return@withLock
        if (still.definition.id != s.definition.id || still.stepIndex != s.stepIndex) return@withLock
        log(TAG, VERBOSE) { "previous(${s.definition.id.raw}): ${s.stepIndex} -> ${s.stepIndex - 1}" }
        _session.value = still.copy(stepIndex = still.stepIndex - 1)
    }

    /**
     * Exit the current session and suppress this tour for the rest of the app process.
     * The skip is in-memory only, so after the app is restarted the tour becomes eligible again.
     */
    override suspend fun skipForNow() = mutationMutex.withLock {
        val s = _session.value ?: return@withLock
        log(TAG) { "skipForNow(${s.definition.id.raw})" }
        skippedThisSession += s.definition.id.raw
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

    /**
     * Flip the global master switch off — no tour starts again until the user taps "Reset guided
     * tours" in settings (which re-enables and clears per-tour state). Per-tour [TourPreferences]
     * are intentionally left untouched: [shouldStart] gates on this flag first, so the flag alone
     * suppresses everything. The session is cleared BEFORE persisting so the overlay disappears
     * immediately — the DataStore write can settle after.
     */
    suspend fun disableAllTours() = mutationMutex.withLock {
        log(TAG) { "disableAllTours() (active=${_session.value?.definition?.id?.raw})" }
        _session.value = null
        routeAtStart = null
        generalSettings.isGuidedToursEnabled.value(false)
    }

    suspend fun complete() {
        val onComplete = mutationMutex.withLock { completeLocked() }
        onComplete?.invoke()
    }

    /**
     * Signal from the host that the session for [tourId] has shown at least one step (anchored or
     * centerless). Lets [completeLocked] tell "user walked the whole tour" apart from "every step
     * grace-skipped because its target never registered". The id guard rejects a late callback
     * from a previous session that already ended (e.g. tour A's render arriving after tour B start).
     */
    fun markStepRendered(tourId: TourId) {
        if (_session.value?.definition?.id == tourId) sessionStepRendered = true
    }

    private suspend fun completeLocked(): (suspend () -> Unit)? {
        val s = _session.value ?: return null
        if (!sessionStepRendered) {
            // The whole tour fell through on missing-target grace-skips without a single step ever
            // being shown (anchors not registered yet, wrong target ids, or a transient layout
            // race). Persisting "completed" would burn the tour forever for something the user
            // never saw — treat it as skip-for-now instead, so it stays eligible after a restart.
            log(TAG, WARN) { "complete(${s.definition.id.raw}): no step ever rendered, skipping instead" }
            skippedThisSession += s.definition.id.raw
            _session.value = null
            routeAtStart = null
            return null
        }
        log(TAG) { "complete(${s.definition.id.raw})" }
        persistCompleted(s.definition.id.raw)
        _session.value = null
        routeAtStart = null
        return s.definition.onComplete
    }

    suspend fun reset() = mutationMutex.withLock {
        log(TAG) { "reset()" }
        skippedThisSession.clear()
        generalSettings.tourPreferences.value(TourPreferences())
        // Resetting brings tours back even if the user opted out during onboarding.
        generalSettings.isGuidedToursEnabled.value(true)
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
