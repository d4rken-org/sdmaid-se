package eu.darken.sdmse.setup.accessibility

import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.automation.core.AutomationController
import eu.darken.sdmse.automation.core.AutomationService
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.datastore.value
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
    private val deviceDetective: DeviceDetective,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)
    override val state = refreshTrigger.mapLatest {
        val isServiceEnabled = automationController.isServiceEnabled()
        log(TAG) { "isServiceEnabled=$isServiceEnabled" }

        val isServiceRunning = automationController.isServiceLaunched()
        log(TAG) { "isServiceRunning=$isServiceRunning" }

        val mightBeRestricted = context.mightBeRestrictedDueToSideload()
        log(TAG) { "mightBeRestricted=$mightBeRestricted" }

        val hasPassedRestrictions = generalSettings.hasPassedAppOpsRestrictions.value()
        log(TAG) { "hasPassedRestrictions=$hasPassedRestrictions" }

        val hasTriggeredRestrictions = generalSettings.hasTriggeredRestrictions.value()
        log(TAG) { "hasTriggeredRestrictions=$hasTriggeredRestrictions" }

        // https://cs.android.com/android/platform/superproject/+/master:packages/apps/Settings/src/com/android/settings/applications/appinfo/AppInfoDashboardFragment.java;l=520
        val showAppOpsRestrictionHint = !hasPassedRestrictions && hasTriggeredRestrictions && mightBeRestricted

        // Settings details screen needs to have the system UID, not ours, otherwise the appops setting is invisible
        val liftRestrictionsIntent = Intent(Settings.ACTION_APPLICATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return@mapLatest State(
            hasConsent = generalSettings.hasAcsConsent.value(),
            isServiceEnabled = isServiceEnabled,
            isServiceRunning = isServiceRunning,
            needsAutostart = deviceDetective.isXiaomi(),
            liftRestrictionsIntent = liftRestrictionsIntent,
            showAppOpsRestrictionHint = showAppOpsRestrictionHint
        )
    }

    suspend fun setAllow(allowed: Boolean) {
        log(TAG) { "setAllow($allowed)" }
        if (!allowed) {
            AutomationService.instance?.let {
                log(TAG) { "Disabling active accessibility service" }
                it.disableSelf()
            }
        }
        generalSettings.hasAcsConsent.value(allowed)
        generalSettings.hasTriggeredRestrictions.value(context.mightBeRestrictedDueToSideload())
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = rngString
    }

    data class State(
        val hasConsent: Boolean?,
        val isServiceEnabled: Boolean,
        val isServiceRunning: Boolean,
        val needsAutostart: Boolean,
        val liftRestrictionsIntent: Intent,
        val showAppOpsRestrictionHint: Boolean,
    ) : SetupModule.State {

        override val isComplete: Boolean = (isServiceEnabled && isServiceRunning) || hasConsent == false

    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AccessibilitySetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Accessibility", "Module")
    }
}