package eu.darken.sdmse.setup.automation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.automation.core.AutomationManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationSetupModule @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
    private val automationManager: AutomationManager,
    private val deviceDetective: DeviceDetective,
    rootManager: RootManager,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)
    override val state: Flow<SetupModule.State> = combine(
        rootManager.useRoot,
        refreshTrigger
    ) { useRoot, _ ->
        val isServiceEnabled = automationManager.isServiceEnabled()
        log(TAG) { "isServiceEnabled=$isServiceEnabled" }

        val isServiceRunning = automationManager.isServiceLaunched()
        log(TAG) { "isServiceRunning=$isServiceRunning" }

        val canSelfEnable = automationManager.canSelfEnable()
        log(TAG) { "canSelfEnable=$canSelfEnable" }

        val isShortcutOrButtonEnabled = automationManager.isShortcutOrButtonEnabled()
        log(TAG) { "isShortcutOrButtonEnabled=$isShortcutOrButtonEnabled" }

        val mightBeRestricted = context.mightBeRestrictedDueToSideload()
        log(TAG) { "mightBeRestricted=$mightBeRestricted" }

        val hasPassedRestrictions = generalSettings.hasPassedAppOpsRestrictions.value()
        log(TAG) { "hasPassedRestrictions=$hasPassedRestrictions" }

        val hasTriggeredRestrictions = generalSettings.hasTriggeredRestrictions.value()
        log(TAG) { "hasTriggeredRestrictions=$hasTriggeredRestrictions" }

        // https://cs.android.com/android/platform/superproject/+/master:packages/apps/Settings/src/com/android/settings/applications/appinfo/AppInfoDashboardFragment.java;l=520
        val showAppOpsRestrictionHint = !hasPassedRestrictions
                && hasTriggeredRestrictions
                && mightBeRestricted
                && !canSelfEnable
        log(TAG) { "showAppOpsRestrictionHint=$showAppOpsRestrictionHint" }

        // Settings details screen needs to have the system UID, not ours, otherwise the appops setting is invisible
        val liftRestrictionsIntent = Intent(Settings.ACTION_APPLICATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        log(TAG) { "useRoot: $useRoot" }

        @Suppress("USELESS_CAST")
        Result(
            isNotRequired = useRoot,
            hasConsent = generalSettings.hasAcsConsent.value(),
            canSelfEnable = canSelfEnable,
            isServiceEnabled = isServiceEnabled,
            isServiceRunning = isServiceRunning,
            isShortcutOrButtonEnabled = isShortcutOrButtonEnabled,
            needsXiaomiAutostart = deviceDetective.getROMType() == RomType.MIUI && !canSelfEnable,
            liftRestrictionsIntent = liftRestrictionsIntent,
            showAppOpsRestrictionHint = showAppOpsRestrictionHint,
            settingsIntent = when (deviceDetective.getROMType()) {
                RomType.ANDROID_TV -> Intent(Settings.ACTION_SETTINGS)
                else -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
        ) as SetupModule.State
    }
        .onEach { log(TAG) { "New ACS setup state: $it" } }
        .onStart { emit(Loading()) }
        .replayingShare(appScope)

    suspend fun setAllow(allowed: Boolean) {
        log(TAG) { "setAllow($allowed)" }
        if (!allowed) {
            automationManager.currentService.first()?.let {
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

    data class Loading(
        override val startAt: Instant = Instant.now(),
    ) : SetupModule.State.Loading {
        override val type: SetupModule.Type = SetupModule.Type.AUTOMATION
    }

    data class Result(
        val isNotRequired: Boolean,
        val hasConsent: Boolean?,
        val canSelfEnable: Boolean,
        val isServiceEnabled: Boolean,
        val isServiceRunning: Boolean,
        val isShortcutOrButtonEnabled: Boolean,
        val needsXiaomiAutostart: Boolean,
        val liftRestrictionsIntent: Intent,
        val showAppOpsRestrictionHint: Boolean,
        val settingsIntent: Intent,
    ) : SetupModule.State.Current {

        override val type: SetupModule.Type = SetupModule.Type.AUTOMATION

        override val isComplete: Boolean = when {
            isNotRequired -> true // ACS not needed

            hasConsent == true -> when { // User wants ACS
                isServiceEnabled && isServiceRunning -> true // ACS is enabled and running
                canSelfEnable -> true // Not running but can enable on-demand
                else -> false // User action needed
            }

            hasConsent == false -> true // User doesn't want ACS
            else -> false // Consent is NULL, need user decision
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AutomationSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Automation", "Module")
    }
}