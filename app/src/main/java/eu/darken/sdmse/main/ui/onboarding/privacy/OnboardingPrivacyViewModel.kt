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
    val motdSettings: MotdSettings,
    private val webpageTool: WebpageTool,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val state = motdSettings.isMotdEnabled.flow
        .map { isMotdEnabled ->
            State(
                isMotdEnabled = isMotdEnabled,
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

    data class State(
        val isMotdEnabled: Boolean,
    )

    companion object {
        private val TAG = logTag("Onboarding", "Privacy", "ViewModel")
    }
}