package eu.darken.sdmse.setup.accessibility

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.automation.core.AutomationController
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

@Reusable
class AccessibilitySetupModule @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
    private val automationController: AutomationController,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)
    override val state = refreshTrigger.mapLatest {
        val isServiceEnabled = automationController.isServiceEnabled()
        log(TAG) { "isServiceEnabled=$isServiceEnabled" }

        val isServiceRunning = automationController.isServiceLaunched()
        log(TAG) { "isServiceRunning=$isServiceRunning" }

        return@mapLatest State(
            isServiceEnabled = isServiceEnabled,
            isServiceRunning = isServiceRunning,
        )
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = rngString
    }

    data class State(
        val isServiceEnabled: Boolean,
        val isServiceRunning: Boolean,
    ) : SetupModule.State {

        override val isComplete: Boolean = isServiceEnabled && isServiceRunning

    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AccessibilitySetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Accessibility", "Module")
    }
}