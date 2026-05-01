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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoFoss,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    private val route = UpgradeRoute.from(handle)

    val snackbarEvents = SingleEventFlow<Int>()
    val toastEvents = SingleEventFlow<Int>()

    init {
        if (!route.forced) {
            upgradeRepo.upgradeInfo
                .filter { it.isPro }
                .take(1)
                .onEach { navUp() }
                .launchInViewModel()
        }

        upgradeRepo.upgradeInfo
            .filter { !it.isPro && it.error != null }
            .onEach { current ->
                @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                errorEvents.tryEmit(current.error!!)
            }
            .launchInViewModel()
    }

    fun goGithubSponsors() {
        log(TAG) { "goGithubSponsors()" }
        handle[KEY_SPONSOR_PRESSED_AT] = SystemClock.elapsedRealtime()
        upgradeRepo.openGithubSponsorsPage()
    }

    fun checkSponsorReturn() = launch {
        val pressedAt = handle.remove<Long>(KEY_SPONSOR_PRESSED_AT) ?: return@launch
        val elapsed = SystemClock.elapsedRealtime() - pressedAt
        log(TAG) { "checkSponsorReturn(): elapsed=${elapsed}ms" }

        if (elapsed < SPONSOR_DELAY_MS) {
            log(TAG) { "checkSponsorReturn(): Too quick, showing snackbar" }
            snackbarEvents.tryEmit(R.string.upgrade_screen_sponsor_return_too_quick)
        } else {
            log(TAG) { "checkSponsorReturn(): Delay passed, persisting upgrade" }
            upgradeRepo.persistUpgrade()
            toastEvents.tryEmit(R.string.upgrade_screen_thanks_toast)
        }
    }

    companion object {
        private const val KEY_SPONSOR_PRESSED_AT = "sponsor_pressed_at"
        private const val SPONSOR_DELAY_MS = 5_000L
        private val TAG = logTag("Upgrade", "ViewModel")
    }
}
