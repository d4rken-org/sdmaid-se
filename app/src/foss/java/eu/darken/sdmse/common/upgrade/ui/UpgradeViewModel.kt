package eu.darken.sdmse.common.upgrade.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoFoss
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoFoss,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs by handle.navArgs<UpgradeFragmentArgs>()

    init {
        if (!navArgs.forced) {
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
                // Linter bug
                @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                errorEvents.postValue(current.error!!)
            }
            State()
        }
        .asLiveData2()

    fun goGithubSponsors() {
        log(TAG) { "goGithubSponsors()" }
        upgradeRepo.launchGithubSponsorsUpgrade()
        popNavStack()
    }

    data class State(
        val tbd: UUID = UUID.randomUUID()
    )

    companion object {
        private val TAG = logTag("Upgrade", "ViewModel")
    }
}