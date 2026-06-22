package eu.darken.sdmse.common.theming

import androidx.appcompat.app.AppCompatDelegate
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.main.core.GeneralSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Theming @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
) {

    fun setup() {
        log(TAG) { "setup()" }

        generalSettings.themeMode.flow
            .onEach { mode ->
                withContext(dispatcherProvider.Main) { mode.applyMode() }
            }
            .setupCommonEventHandlers(TAG) { "setup" }
            .launchIn(appScope)

        generalSettings.themeMode.valueBlocking.let {
            log(TAG) { "Applying initial themeMode setting: $it" }
            it.applyMode()
        }
    }

    private fun ThemeMode.applyMode() = when (this) {
        ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    companion object {
        private val TAG = logTag("Theming")
    }
}
