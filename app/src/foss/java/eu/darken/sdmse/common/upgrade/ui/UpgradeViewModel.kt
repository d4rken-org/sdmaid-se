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
    // options. Upgrading wins on EVERY route, not just manage: forced routes (Pro-locked settings)
    // stay open after the sponsor flow completes, and the pitch with its live sponsor button reads
    // as "sponsoring didn't work" to a fresh supporter — the toast alone is too transient for a
    // money moment without a receipt behind it. null until the route is bound.
    internal val state: StateFlow<State> = combine(
        routeFlow,
        upgradeRepo.upgradeInfo,
        handle.getStateFlow(KEY_SHOW_UPGRADE_OPTIONS, false),
    ) { route, info, showOptions ->
        val view = when {
            route == null -> null
            info.isPro -> FossUpgradeView.STATUS_UPGRADED
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

    /** Armed variant: the pitch's sponsor button, which starts the return-after-5s unlock heuristic. */
    fun goGithubSponsors() {
        log(TAG) { "goGithubSponsors()" }
        if (hasPendingSponsorLaunch()) {
            log(TAG) { "A sponsor launch is already awaiting its return" }
            return
        }
        // Only arm the heuristic if the page actually opened; otherwise an unrelated later
        // background/foreground round-trip would grant supporter status with no page ever shown.
        if (!upgradeRepo.openGithubSponsorsPage()) {
            log(TAG) { "Sponsor page didn't open; not arming the unlock heuristic" }
            return
        }
        handle[KEY_SPONSOR_PRESSED_AT] = SystemClock.elapsedRealtime()
    }

    /**
     * Unarmed variant: the status view's donate button. An existing supporter re-visiting the page
     * must not re-arm the unlock heuristic — there is nothing left to unlock.
     */
    fun openSponsors() {
        log(TAG) { "openSponsors()" }
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

        try {
            // Evaluated before the duration: an already upgraded supporter (recurring donation button)
            // has nothing left to unlock, so this fast path exists for the UX — return quietly, no
            // redundant write attempt and no thanks toast for an unlock that already happened. Data
            // integrity is not this guard's job: the repo's create-only transaction owns that.
            if (upgradeRepo.upgradeInfo.first().isPro) {
                log(TAG) { "checkSponsorReturn(): Already upgraded, staying quiet" }
                return@launch
            }

            val elapsed = SystemClock.elapsedRealtime() - pressedAt
            log(TAG) { "checkSponsorReturn(): elapsed=${elapsed}ms" }

            if (elapsed < SPONSOR_DELAY_MS) {
                log(TAG) { "checkSponsorReturn(): Too quick, showing snackbar" }
                snackbarEvents.tryEmit(R.string.upgrade_screen_sponsor_return_too_quick)
            } else {
                log(TAG) { "checkSponsorReturn(): Delay passed, persisting upgrade" }
                val created = upgradeRepo.persistUpgrade()
                if (created) {
                    toastEvents.tryEmit(R.string.upgrade_screen_thanks_toast)
                } else {
                    // The isPro fast-path read a stale emission; the transaction kept the existing record.
                    log(TAG) { "checkSponsorReturn(): Record already existed, staying quiet" }
                }
            }
        } catch (e: Exception) {
            // The marker was consumed above; neither a failed entitlement read nor a failed write may
            // eat the user's valid sponsor visit — restore it so the next return/resume can retry the
            // unlock. Conditional: the user may have armed a NEWER launch while this attempt was
            // suspended, and that one must survive. The contains-check has a small check-then-act
            // window against a concurrent new arm; accepted — the create-only transaction owns data
            // integrity, a wrong winner only changes which REAL visit's timestamp gates the unlock.
            // Rethrow unconditionally: cancellation is not swallowed.
            if (!handle.contains(KEY_SPONSOR_PRESSED_AT)) {
                handle[KEY_SPONSOR_PRESSED_AT] = pressedAt
            }
            throw e
        }
    }

    companion object {
        private const val KEY_SPONSOR_PRESSED_AT = "sponsor_pressed_at"
        private const val KEY_SHOW_UPGRADE_OPTIONS = "show_upgrade_options"
        private const val SPONSOR_DELAY_MS = 5_000L
        private val TAG = logTag("Upgrade", "ViewModel")
    }
}
