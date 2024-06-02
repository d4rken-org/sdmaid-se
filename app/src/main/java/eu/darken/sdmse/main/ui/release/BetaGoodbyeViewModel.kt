package eu.darken.sdmse.main.ui.release

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.updater.UpdateChecker
import eu.darken.sdmse.main.core.release.ReleaseSettings
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class BetaGoodbyeViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val releaseSettings: ReleaseSettings,
    private val webpageTool: WebpageTool,
    private val updateChecker: UpdateChecker,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    fun consentPrerelease(consent: Boolean) = launch {
        log(TAG) { "consentPrerelease($consent)" }
        when (BuildConfigWrap.FLAVOR) {
            BuildConfigWrap.Flavor.GPLAY -> {
                if (!consent) {
                    webpageTool.open("https://play.google.com/apps/testing/eu.darken.sdmse")
                }
            }

            BuildConfigWrap.Flavor.FOSS -> {
                if (!consent && BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE) {
                    launch {
                        val latest = updateChecker.getLatest(UpdateChecker.Channel.PROD)
                        if (latest != null) {
                            updateChecker.viewUpdate(latest)
                        } else {
                            webpageTool.open("https://github.com/d4rken-org/sdmaid-se/releases/latest")
                        }
                    }
                }
            }

            BuildConfigWrap.Flavor.NONE -> throw IllegalStateException("Why is there no flavor?")
        }
        releaseSettings.wantsBeta.value(consent)
        releaseSettings.releasePartyAt.value(Instant.now())
        popNavStack()
    }

    companion object {
        private val TAG = logTag("Release", "BetaGoodbye", "ViewModel")
    }
}