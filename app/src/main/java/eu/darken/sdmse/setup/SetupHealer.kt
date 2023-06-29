package eu.darken.sdmse.setup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.AutomationService
import eu.darken.sdmse.common.SystemSettingsProvider
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.shizuku.canUseShizukuNow
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.ourInstall
import eu.darken.sdmse.setup.automation.AutomationSetupModule
import eu.darken.sdmse.setup.notification.NotificationSetupModule
import eu.darken.sdmse.setup.storage.StorageSetupModule
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetupHealer @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val settingsProvider: SystemSettingsProvider,
    private val shizukuManager: ShizukuManager,
    private val rootManager: RootManager,
    private val pkgOps: PkgOps,
    private val userManager2: UserManager2,
    private val automationSetupModule: AutomationSetupModule,
    private val usageStatsSetupModule: UsageStatsSetupModule,
    private val notificationSetupModule: NotificationSetupModule,
    private val storageSetupModule: StorageSetupModule,
    private val dataAreaManager: DataAreaManager,
) {

    private val internalState = MutableStateFlow(State())
    val state: Flow<State> = internalState

    init {
        combine(
            rootManager.useRoot,
            shizukuManager.useShizuku,
            automationSetupModule.state,
            usageStatsSetupModule.state,
            notificationSetupModule.state,
            storageSetupModule.state,
        ) { useRoot, useShizuku,
            acsState, usageState, notifState, storageState ->

            val hasHealingPowers = useRoot || useShizuku

            val hasIncomplete =
                !acsState.isComplete || !usageState.isComplete || !notifState.isComplete || !storageState.isComplete

            if (hasHealingPowers && hasIncomplete) {
                internalState.value = internalState.value.copy(isWorking = true)
            }

            var reloadDataAreas = false
            try {
                if (acsState.tryHeal()) automationSetupModule.refresh()
                if (usageState.tryHeal()) usageStatsSetupModule.refresh()
                if (notifState.tryHeal()) notificationSetupModule.refresh()
                if (storageState.tryHeal()) {
                    storageSetupModule.refresh()
                    reloadDataAreas = true
                }

                if (reloadDataAreas) dataAreaManager.reload()
            } finally {
                internalState.value = internalState.value.copy(isWorking = false)
            }
        }
            .catch { log(TAG, ERROR) { "Healing failed: ${it.asLog()}" } }
            .launchIn(appScope)
    }

    private suspend fun ensureGrantPermission(): Boolean {
        if (shizukuManager.canUseShizukuNow()) {
            log(TAG, VERBOSE) { "ensureGrantPermission() available via Shizuku" }
            return true
        }

        if (rootManager.canUseRootNow()) {
            log(TAG, VERBOSE) { "ensureGrantPermission() available via Root" }
            return true
        }

        log(TAG, VERBOSE) { "ensureGrantPermission() is not available" }
        return false
    }

    private suspend fun ensureSecureSettings(): Boolean {
        if (settingsProvider.hasSecureWriteAccess()) {
            log(TAG, VERBOSE) { "ensureSecureSettings(): We already have secure settings access" }
            return true
        }

        if (!ensureGrantPermission()) {
            log(TAG) { "ensureSecureSettings(): Can't gain grant permissions" }
            return false
        }
        pkgOps.grantPermission(userManager2.ourInstall(), Permission.WRITE_SECURE_SETTINGS)

        return settingsProvider.hasSecureWriteAccess().also {
            if (it) log(TAG, INFO) { "We were able to gain secure settings access :)" }
            else log(TAG, INFO) { "We were not able to gain secure settings access :(" }
        }
    }

    private suspend fun AutomationSetupModule.State.tryHeal(): Boolean {
        if (isComplete || hasConsent != true || isServiceEnabled) {
            return false
        }

        if (!ensureSecureSettings()) {
            log(TAG, WARN) { "ACS: We don't have secure settings access." }
            return false
        }

        log(TAG) { "ACS: Setting up..." }

        val beforeAcs: String? = settingsProvider.get(
            SystemSettingsProvider.Type.SECURE,
            SETTINGS_KEY_ACS
        )
        log(TAG) { "ACS: Before writing ACS settings: $beforeAcs" }

        val splitAcs = beforeAcs
            ?.split(":")
            ?.filter { it.isNotBlank() }
            ?: emptySet()
        if (splitAcs.contains(SETTINGS_VALUE_OUR_ACS)) {
            log(TAG, ERROR) { "ACS: Service isn't running but we are already enabled?" }
        } else {
            val newAcsValue = splitAcs.plus(SETTINGS_VALUE_OUR_ACS).joinToString(":")
            settingsProvider.put(
                SystemSettingsProvider.Type.SECURE,
                SETTINGS_KEY_ACS, newAcsValue
            )
            val afterAcs: String? = settingsProvider.get(
                SystemSettingsProvider.Type.SECURE,
                SETTINGS_KEY_ACS
            )
            log(TAG) { "ACS: After writings ACS settings: $afterAcs" }
        }

        return true
    }

    private suspend fun UsageStatsSetupModule.State.tryHeal(): Boolean {
        if (isComplete || missingPermission.isEmpty()) {
            return false
        }

        if (!ensureGrantPermission()) {
            log(TAG) { "USAGE_STATS: We don't have grant access permission" }
            return false
        }

        log(TAG) { "USAGE_STATS: Setting up..." }

        log(TAG) { "USAGE_STATS: Granting $missingPermission..." }
        pkgOps.setAppOps(userManager2.ourInstall(), PkgOps.AppOpsKey.GET_USAGE_STATS, PkgOps.AppOpsValue.ALLOW)

        val allGranted = missingPermission.all { it.isGranted(context) }
        log(TAG) { "USAGE_STATS: allGranted=$allGranted" }

        return allGranted
    }

    private suspend fun StorageSetupModule.State.tryHeal(): Boolean {
        if (isComplete || missingPermission.isEmpty()) {
            return false
        }

        if (!ensureGrantPermission()) {
            log(TAG) { "STORAGE: We don't have grant access permission" }
            return false
        }

        log(TAG) { "STORAGE: Setting up..." }

        log(TAG) { "STORAGE: Granting $missingPermission..." }
        pkgOps.setAppOps(userManager2.ourInstall(), PkgOps.AppOpsKey.MANAGE_EXTERNAL_STORAGE, PkgOps.AppOpsValue.ALLOW)

        val allGranted = missingPermission.all { it.isGranted(context) }
        log(TAG) { "STORAGE: allGranted=$allGranted" }

        return allGranted
    }

    private suspend fun NotificationSetupModule.State.tryHeal(): Boolean {
        if (isComplete || missingPermission.isEmpty()) {
            return false
        }

        if (!ensureGrantPermission()) {
            log(TAG) { "NOTIFICATIONS: We don't have grant access permission" }
            return false
        }

        log(TAG) { "NOTIFICATIONS: Setting up..." }

        missingPermission.forEach {
            log(TAG) { "NOTIFICATIONS: Granting $it..." }
            pkgOps.grantPermission(userManager2.ourInstall(), it)
        }

        val allGranted = missingPermission.all { it.isGranted(context) }
        log(TAG) { "NOTIFICATIONS: allGranted=$allGranted" }

        return allGranted
    }

    data class State(
        val isWorking: Boolean = false
    )

    companion object {
        private val SETTINGS_VALUE_OUR_ACS = "eu.darken.sdmse/${AutomationService::class.qualifiedName!!}"
        private const val SETTINGS_KEY_ACS = "enabled_accessibility_services"
        private val TAG = logTag("Setup", "Healer")
    }
}