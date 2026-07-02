package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.root.RootManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchedulerAppCleanerAdvisorImpl @Inject constructor(
    private val appCleanerSettings: AppCleanerSettings,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
) : SchedulerAppCleanerAdvisor {

    // Reactive over both the AppCleaner settings and root/ADB availability, so the warning refreshes when the user
    // grants/loses Shizuku or root mid-session, not just when an AppCleaner setting changes.
    override val acsScheduleRisk: Flow<AcsScheduleRisk> = combine(
        appCleanerSettings.includeInaccessibleEnabled.flow,
        appCleanerSettings.filterDefaultCachesPublicEnabled.flow,
        appCleanerSettings.filterDefaultCachesPrivateEnabled.flow,
        appCleanerSettings.includeSystemAppsEnabled.flow,
        combine(rootManager.useRoot, adbManager.useAdb) { root, adb -> root to adb },
    ) { includeInaccessible, filterPublic, filterPrivate, includeSystemApps, (hasRoot, hasAdb) ->
        classifyAcsScheduleRisk(
            includeInaccessible = includeInaccessible,
            filterPublic = filterPublic,
            filterPrivate = filterPrivate,
            hasRoot = hasRoot,
            hasAdb = hasAdb,
            includeSystemApps = includeSystemApps,
        )
    }
}

/**
 * Mirrors the AppCleaner scanner/deleter gates that decide whether inaccessible-cache clearing falls back to ACS:
 * - `AppScanner.determineInaccessibleCaches` skips entirely without [includeInaccessible], without both default-cache
 *   filters, or when root is available.
 * - `InaccessibleDeleter.trimCachesWithAdb` clears non-system apps via ADB/Shizuku; system apps still need ACS.
 */
internal fun classifyAcsScheduleRisk(
    includeInaccessible: Boolean,
    filterPublic: Boolean,
    filterPrivate: Boolean,
    hasRoot: Boolean,
    hasAdb: Boolean,
    includeSystemApps: Boolean,
): AcsScheduleRisk = when {
    !includeInaccessible || !filterPublic || !filterPrivate -> AcsScheduleRisk.NONE
    hasRoot -> AcsScheduleRisk.NONE
    !hasAdb -> AcsScheduleRisk.ACS_REQUIRED_ALL
    includeSystemApps -> AcsScheduleRisk.ACS_REQUIRED_SYSTEM_APPS_ONLY
    else -> AcsScheduleRisk.NONE
}
