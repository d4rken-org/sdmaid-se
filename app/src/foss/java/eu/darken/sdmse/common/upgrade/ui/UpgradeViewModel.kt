package eu.darken.sdmse.common.upgrade.ui

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import androidx.navigation.toRoute
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.R
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoFoss
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoFoss,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val route = UpgradeRoute.from(handle)

    val snackbarEvents = SingleLiveEvent<Int>()
    val toastEvents = SingleLiveEvent<Int>()

    init {
        if (!route.forced) {
            upgradeRepo.upgradeInfo
                .filter { it.isPro }
                .take(1)
                .onEach { popNavStack() }
                .launchInViewModel()
        }
    }

    val state = upgradeRepo.upgradeInfo
        .map { it as UpgradeRepoFoss.Info }
        .map { current ->
            if (!current.isPro && current.error != null) {
                @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                errorEvents.postValue(current.error!!)
            }
            State()
        }
        .asLiveData2()

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
            snackbarEvents.postValue(R.string.upgrade_screen_sponsor_return_too_quick)
        } else {
            log(TAG) { "checkSponsorReturn(): Delay passed, persisting upgrade" }
            upgradeRepo.persistUpgrade()
            toastEvents.postValue(R.string.upgrade_screen_thanks_toast)
        }
    }

    data class State(
        val tbd: UUID = UUID.randomUUID()
    )

    companion object {
        private const val KEY_SPONSOR_PRESSED_AT = "sponsor_pressed_at"
        private const val SPONSOR_DELAY_MS = 5_000L
        private val TAG = logTag("Upgrade", "ViewModel")
    }
}
