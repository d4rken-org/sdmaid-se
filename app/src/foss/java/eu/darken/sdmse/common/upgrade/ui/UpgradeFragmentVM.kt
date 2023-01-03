package eu.darken.sdmse.common.upgrade.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoFoss
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class UpgradeFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepoFoss: UpgradeRepoFoss,
    private val webpageTool: WebpageTool,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    fun goGithubSponsors() {
        log(TAG) { "goGithubSponsors()" }
        upgradeRepoFoss.launchGithubSponsorsUpgrade()
        popNavStack()
    }

    companion object {
        private val TAG = logTag("Upgrade", "Fragment", "VM")
    }
}