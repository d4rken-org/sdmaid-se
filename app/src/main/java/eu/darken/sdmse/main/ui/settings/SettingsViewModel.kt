package eu.darken.sdmse.main.ui.settings

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.ClipboardHelper
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.main.core.CurriculumVitae
import eu.darken.sdmse.setup.SetupManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @Suppress("unused") val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val setupManager: SetupManager,
    private val webpageTool: WebpageTool,
    private val clipboardHelper: ClipboardHelper,
    private val curriculumVitae: CurriculumVitae,
) : ViewModel4(dispatcherProvider, TAG) {

    val events = SingleEventFlow<SettingEvents>()

    val state: StateFlow<State> = setupManager.state
        .map { State(setupDone = it.isDone) }
        .safeStateIn(
            initialValue = State(),
            onError = { State() },
        )

    fun openWebsite(url: String) {
        webpageTool.open(url)
    }

    fun openPrivacyPolicy() {
        webpageTool.open(SdmSeLinks.PRIVACY_POLICY)
    }

    private suspend fun getVersionText() = """
            Build: `${BuildConfigWrap.VERSION_DESCRIPTION}`
            Update history: `${curriculumVitae.history.first()}`
            ROM: `${Build.FINGERPRINT}`
        """.trimIndent()

    fun showVersionInfos() = launch {
        events.emit(SettingEvents.ShowVersionInfo(getVersionText()))
    }

    fun copyVersionInfos() = launch {
        clipboardHelper.copyToClipboard(getVersionText())
    }

    data class State(
        // Nullable = "not yet known"; avoids a one-frame flash of the setup tint for set-up users
        // before the first upstream emission under WhileSubscribed.
        val setupDone: Boolean? = null,
    )

    companion object {
        private val TAG = logTag("Settings", "ViewModel")
    }
}
