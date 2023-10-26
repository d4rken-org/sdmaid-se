package eu.darken.sdmse.main.ui.onboarding.privacy

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.updater.UpdateChecker
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.motd.MotdSettings
import eu.darken.sdmse.main.ui.dashboard.items.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class OnboardingPrivacyViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val motdSettings: MotdSettings,
    private val webpageTool: WebpageTool,
    private val updateChecker: UpdateChecker,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val state = combine(
        motdSettings.isMotdEnabled.flow,
        generalSettings.isUpdateCheckEnabled.flow,
        flow { emit(updateChecker.isCheckSupported()) }
    ) { isMotdEnabled, isUpdateCheckEnabled, isUpdateCheckSupported ->
        State(
            isMotdEnabled = isMotdEnabled,
            isUpdateCheckEnabled = isUpdateCheckEnabled,
            isUpdateCheckSupported = isUpdateCheckSupported,
        )
    }
        .asLiveData2()

    fun goPrivacyPolicy() {
        log(TAG) { "goPrivacyPolicy()" }
        webpageTool.open(SdmSeLinks.PRIVACY_POLICY)
    }

    fun toggleMotd() {
        log(TAG) { "toggleMotd()" }
        motdSettings.isMotdEnabled.valueBlocking = !motdSettings.isMotdEnabled.valueBlocking
    }

    fun toggleUpdateCheck() {
        log(TAG) { "toggleUpdateCheck()" }
        generalSettings.isUpdateCheckEnabled.valueBlocking = !generalSettings.isUpdateCheckEnabled.valueBlocking
    }

    data class State(
        val isMotdEnabled: Boolean,
        val isUpdateCheckEnabled: Boolean,
        val isUpdateCheckSupported: Boolean,
    )

    companion object {
        private val TAG = logTag("Onboarding", "Privacy", "ViewModel")
    }
}