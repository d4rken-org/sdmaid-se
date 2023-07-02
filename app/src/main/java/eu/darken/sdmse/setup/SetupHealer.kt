package eu.darken.sdmse.setup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.ourInstall
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
    private val shizukuManager: ShizukuManager,
    private val rootManager: RootManager,
    private val setupHelper: SetupHelper,
    private val pkgOps: PkgOps,
    private val userManager2: UserManager2,
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
            usageStatsSetupModule.state,
            notificationSetupModule.state,
            storageSetupModule.state,
        ) { useRoot, useShizuku,
            usageState, notifState, storageState ->

            val hasHealingPowers = useRoot || useShizuku

            val hasIncomplete = !usageState.isComplete || !notifState.isComplete || !storageState.isComplete

            if (hasHealingPowers && hasIncomplete) {
                internalState.value = internalState.value.copy(isWorking = true)
            }

            var reloadDataAreas = false
            try {
                if (usageState.tryHeal()) usageStatsSetupModule.refresh()
                if (notifState.tryHeal()) notificationSetupModule.refresh()
                if (storageState.tryHeal()) {
                    storageSetupModule.refresh()
                    reloadDataAreas = true
                }

                if (reloadDataAreas) dataAreaManager.reload()
            } finally {
                internalState.value = internalState.value.copy(
                    healAttemptCount = internalState.value.healAttemptCount + 1,
                    isWorking = false
                )
            }
        }
            .catch { log(TAG, ERROR) { "Healing failed: ${it.asLog()}" } }
            .launchIn(appScope)
    }

    private suspend fun UsageStatsSetupModule.State.tryHeal(): Boolean {
        if (isComplete || missingPermission.isEmpty()) {
            return false
        }

        if (!setupHelper.checkGrantPermissions()) {
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

        if (!setupHelper.checkGrantPermissions()) {
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

        if (!setupHelper.checkGrantPermissions()) {
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
        val healAttemptCount: Int = 0,
        val isWorking: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Setup", "Healer")
    }
}