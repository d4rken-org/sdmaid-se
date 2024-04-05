package eu.darken.sdmse.main.ui.settings

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.ClipboardHelper
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.uix.ViewModel2
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.CurriculumVitae
import eu.darken.sdmse.setup.SetupManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @Suppress("unused") val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepo,
    private val setupManager: SetupManager,
    private val webpageTool: WebpageTool,
    private val clipboardHelper: ClipboardHelper,
    private val curriculumVitae: CurriculumVitae,
) : ViewModel2(dispatcherProvider) {

    val events = SingleLiveEvent<SettingEvents>()

    val state = combine(
        upgradeRepo.upgradeInfo.map { it.isPro },
        setupManager.state.map { it.isDone },
    ) { isPro, isSetUp ->
        State(
            isPro = isPro,
            setupDone = isSetUp,
        )
    }.asLiveData2()

    fun openWebsite(url: String) {
        webpageTool.open(url)
    }

    fun openUpgradeWebsite() {
        webpageTool.open(upgradeRepo.mainWebsite)
    }

    private suspend fun getVersionText() = """
            Build: `${BuildConfigWrap.VERSION_DESCRIPTION}`
            Update history: `${curriculumVitae.history.first()}`
            ROM: `${Build.FINGERPRINT}`
        """.trimIndent()

    fun showVersionInfos() = launch {
        events.postValue(SettingEvents.ShowVersionInfo(getVersionText()))
    }

    fun copyVersionInfos() = launch {
        clipboardHelper.copyToClipboard(getVersionText())
    }

    data class State(
        val isPro: Boolean,
        val setupDone: Boolean,
    )

}