package eu.darken.sdmse.main.core.taskmanager

import kotlinx.coroutines.flow.Flow

/**
 * Advises whether a *background* (scheduled) AppCleaner run would currently rely on the accessibility service to
 * clear inaccessible caches. ACS automation can't run while the screen is off/locked, so such a run would be skipped
 * during unattended schedules.
 *
 * Lives in the app module (composition layer) so the scheduler doesn't need to depend on the AppCleaner tool module.
 */
interface SchedulerAppCleanerAdvisor {
    val acsScheduleRisk: Flow<AcsScheduleRisk>
}

enum class AcsScheduleRisk {
    /** No ACS needed for background cleaning (root available, inaccessible cleaning off, or ADB covers everything). */
    NONE,

    /** All inaccessible caches need ACS (no root, no ADB/Shizuku). */
    ACS_REQUIRED_ALL,

    /** ADB/Shizuku covers normal apps, but system apps still need ACS because "Include system apps" is enabled. */
    ACS_REQUIRED_SYSTEM_APPS_ONLY,
}
