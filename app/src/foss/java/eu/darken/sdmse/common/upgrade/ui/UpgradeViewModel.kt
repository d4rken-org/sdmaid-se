package eu.darken.sdmse.common.upgrade.ui

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoFoss
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoFoss,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    // Route is bound from the Host via bindRoute(); SavedStateHandle.toRoute<>() crashes under Nav3.
    private val routeFlow = MutableStateFlow<UpgradeRoute?>(null)

    fun bindRoute(route: UpgradeRoute) {
        if (routeFlow.value != null) return
        routeFlow.value = route
    }

    val snackbarEvents = SingleEventFlow<Int>()
    val toastEvents = SingleEventFlow<Int>()

    // Which presentation the screen shows. The manage route (settings "upgrade status" entry)
    // gets a status view first; the pitch only appears once a free user asks for the upgrade
    // options. Upgrading wins over that choice — completing the sponsor flow from the pitch must
    // land on the upgraded status, not back on the ask. null until the route is bound.
    internal val state: StateFlow<State> = combine(
        routeFlow,
        upgradeRepo.upgradeInfo,
        handle.getStateFlow(KEY_SHOW_UPGRADE_OPTIONS, false),
    ) { route, info, showOptions ->
        val view = when {
            route == null -> null
            route.manage && info.isPro -> FossUpgradeView.STATUS_UPGRADED
            route.manage && !showOptions -> FossUpgradeView.STATUS_FREE
            else -> FossUpgradeView.PITCH
        }
        // Derived in the same emission as the view on purpose: a sibling flow would let the
        // upgraded status render for a frame without the date it is supposed to carry.
        State(view = view, supporterSince = info.upgradedAt)
    }.safeStateIn(
        initialValue = State(),
        onError = { State(view = FossUpgradeView.PITCH) },
    )

    // internal like FossUpgradeView: the view enum is a screen-local presentation detail.
    internal data class State(
        val view: FossUpgradeView? = null,
        val supporterSince: Instant? = null,
    )

    init {
        routeFlow
            .filterNotNull()
            .take(1)
            .onEach { route ->
                // The manage route is the settings "upgrade status" entry — upgraded users must
                // not be bounced out. Forced routes keep their existing don't-auto-close semantics.
                if (!route.forced && !route.manage) {
                    upgradeRepo.upgradeInfo
                        .filter { it.isPro }
                        .take(1)
                        .onEach { navUp() }
                        .launchInViewModel()
                }
            }
            .launchInViewModel()

        upgradeRepo.upgradeInfo
            .filter { !it.isPro && it.error != null }
            .onEach { current ->
                @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                errorEvents.tryEmit(current.error!!)
            }
            .launchInViewModel()
    }

    fun onShowUpgradeOptions() {
        log(TAG) { "onShowUpgradeOptions()" }
        // Handle-backed: surviving process recreation keeps the user on the pitch they asked for.
        handle[KEY_SHOW_UPGRADE_OPTIONS] = true
    }

    fun goGithubSponsors() {
        log(TAG) { "goGithubSponsors()" }
        handle[KEY_SPONSOR_PRESSED_AT] = SystemClock.elapsedRealtime()
        upgradeRepo.openGithubSponsorsPage()
    }

    /**
     * Whether a sponsor-page launch is still awaiting its return.
     *
     * Handle-backed, so it survives process recreation while the browser is in front — the screen's
     * in-memory return tracker does not, and gating on that alone drops the first return after a
     * recreation.
     */
    fun hasPendingSponsorLaunch(): Boolean = handle.contains(KEY_SPONSOR_PRESSED_AT)

    fun checkSponsorReturn() = launch {
        val pressedAt = handle.remove<Long>(KEY_SPONSOR_PRESSED_AT) ?: return@launch
        val elapsed = SystemClock.elapsedRealtime() - pressedAt
        log(TAG) { "checkSponsorReturn(): elapsed=${elapsed}ms" }

        if (elapsed < SPONSOR_DELAY_MS) {
            // The nudge belongs to the unlock heuristic. An already upgraded user (recurring
            // donation button) has nothing to unlock — peeking at the page needs no feedback.
            if (upgradeRepo.upgradeInfo.first().isPro) {
                log(TAG) { "checkSponsorReturn(): Too quick, but already upgraded, staying quiet" }
            } else {
                log(TAG) { "checkSponsorReturn(): Too quick, showing snackbar" }
                snackbarEvents.tryEmit(R.string.upgrade_screen_sponsor_return_too_quick)
            }
        } else {
            log(TAG) { "checkSponsorReturn(): Delay passed, persisting upgrade" }
            upgradeRepo.persistUpgrade()
            toastEvents.tryEmit(R.string.upgrade_screen_thanks_toast)
        }
    }

    companion object {
        private const val KEY_SPONSOR_PRESSED_AT = "sponsor_pressed_at"
        private const val KEY_SHOW_UPGRADE_OPTIONS = "show_upgrade_options"
        private const val SPONSOR_DELAY_MS = 5_000L
        private val TAG = logTag("Upgrade", "ViewModel")
    }
}
