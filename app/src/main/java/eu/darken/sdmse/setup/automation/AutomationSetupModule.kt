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
import eu.darken.sdmse.automation.core.AutomationController
import eu.darken.sdmse.automation.core.AutomationService
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.SystemSettingsProvider
import eu.darken.sdmse.common.SystemSettingsProvider.*
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationSetupModule @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val generalSettings: GeneralSettings,
    private val automationController: AutomationController,
    private val deviceDetective: DeviceDetective,
    private val settingsProvider: SystemSettingsProvider,
    rootManager: RootManager,
    shizukuManager: ShizukuManager,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)
    override val state = combine(
        rootManager.useRoot,
        shizukuManager.useShizuku,
        refreshTrigger
    ) { useRoot, useShizuku, _ ->
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

        State(
            isNotRequired = useRoot || useShizuku,
            hasConsent = generalSettings.hasAcsConsent.value(),
            isServiceEnabled = isServiceEnabled,
            isServiceRunning = isServiceRunning,
            needsAutostart = deviceDetective.isXiaomi(),
            liftRestrictionsIntent = liftRestrictionsIntent,
            showAppOpsRestrictionHint = showAppOpsRestrictionHint
        )
    }

    init {
        state
            .filter { !it.isComplete && it.hasConsent == true && !it.isServiceEnabled }
            .onEach { currentState ->
                log(TAG, VERBOSE) { "ACS should be enabled, but isn't: $currentState" }

                val hasWriteSettings = settingsProvider.hasSecureWriteAccess()
                if (!hasWriteSettings) return@onEach
                else log(TAG, VERBOSE) { "We have secure settings access, let's troubleshoot" }

                val beforeAcs: String? = settingsProvider.get(Type.SECURE, SETTINGS_KEY_ACS)
                log(TAG) { "Before writing ACS settings: $beforeAcs" }

                val splitAcs = beforeAcs
                    ?.split(":")
                    ?.filter { it.isNotBlank() }
                    ?: emptySet()
                if (splitAcs.contains(SETTINGS_VALUE_OUR_ACS)) {
                    log(TAG, ERROR) { "Service isn't running but we are already enabled?" }
                } else {
                    val newAcsValue = splitAcs.plus(SETTINGS_VALUE_OUR_ACS).joinToString(":")
                    settingsProvider.put(Type.SECURE, SETTINGS_KEY_ACS, newAcsValue)
                    val afterAcs: String? = settingsProvider.get(Type.SECURE, SETTINGS_KEY_ACS)
                    log(TAG) { "After writings ACS settings: $afterAcs" }
                }
            }
            .catch { log(TAG) { "Automatic ACS recovery failed: ${it.asLog()}" } }
            .launchIn(appScope)
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
        val isNotRequired: Boolean,
        val hasConsent: Boolean?,
        val isServiceEnabled: Boolean,
        val isServiceRunning: Boolean,
        val needsAutostart: Boolean,
        val liftRestrictionsIntent: Intent,
        val showAppOpsRestrictionHint: Boolean,
    ) : SetupModule.State {

        override val isComplete: Boolean =
            isNotRequired || (hasConsent == true && isServiceEnabled && isServiceRunning) || hasConsent == false

    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AutomationSetupModule): SetupModule
    }

    companion object {
        private val SETTINGS_VALUE_OUR_ACS = "eu.darken.sdmse/${AutomationService::class.qualifiedName!!}"
        private const val SETTINGS_KEY_ACS = "enabled_accessibility_services"
        private val TAG = logTag("Setup", "Automation", "Module")
    }
}