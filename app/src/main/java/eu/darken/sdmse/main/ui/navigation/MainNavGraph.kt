package eu.darken.sdmse.main.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.fragment.dialog
import androidx.navigation.fragment.fragment
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.ui.AppDetailsRoute
import eu.darken.sdmse.analyzer.ui.AppsRoute
import eu.darken.sdmse.analyzer.ui.ContentRoute
import eu.darken.sdmse.common.navigation.routes.DeviceStorageRoute
import eu.darken.sdmse.analyzer.ui.StorageContentRoute
import eu.darken.sdmse.analyzer.ui.storage.app.AppDetailsFragment
import eu.darken.sdmse.analyzer.ui.storage.apps.AppsFragment
import eu.darken.sdmse.analyzer.ui.storage.content.ContentFragment
import eu.darken.sdmse.analyzer.ui.storage.device.DeviceStorageFragment
import eu.darken.sdmse.analyzer.ui.storage.storage.StorageContentFragment
// AppCleaner — list/details/page converted to Compose, registered via AppCleanerNavigation
// AppCleanerSettingsFragment — converted to Compose, registered via AppCleanerNavigation
// AppControl — list/action-sheet/settings converted to Compose, registered via AppControlNavigation
// LogViewFragment — converted to Compose
import eu.darken.sdmse.common.navigation.routes.DataAreasRoute
import eu.darken.sdmse.common.navigation.routes.DashboardRoute
import eu.darken.sdmse.common.navigation.routes.LogViewRoute
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.navigation.serializableNavType
// PickerFragment — converted to Compose, registered via PickerNavigation
import eu.darken.sdmse.common.pkgs.features.InstallId
// PreviewFragment + PreviewItemFragment — converted to Compose, registered via PreviewNavigation
import eu.darken.sdmse.common.storage.StorageId
// CorpseFinderSettingsFragment — converted to Compose, registered via CorpseFinderNavigation
// Deduplicator — fully converted to Compose, registered via DeduplicatorNavigation
import eu.darken.sdmse.exclusion.ui.PathExclusionEditorRoute
import eu.darken.sdmse.exclusion.ui.PkgExclusionEditorRoute
import eu.darken.sdmse.exclusion.ui.SegmentExclusionEditorRoute
// DataAreasFragment — converted to Compose
// DashboardFragment — converted to Compose
// Onboarding Fragments — converted to Compose
// SettingsFragment — converted to Compose
// DashboardCardConfigFragment — converted to Compose
// SupportContactFormFragment — converted to Compose
// DebugLogSessionsDialog — converted to Compose
import eu.darken.sdmse.scheduler.ui.ScheduleItemRoute
import eu.darken.sdmse.scheduler.ui.SchedulerManagerRoute
// SchedulerSettingsFragment — converted to Compose, registered via SchedulerNavigation
import eu.darken.sdmse.scheduler.ui.manager.SchedulerManagerFragment
import eu.darken.sdmse.scheduler.ui.manager.create.ScheduleItemDialog
// SetupFragment — converted to Compose
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenOptions
// SqueezerSettingsFragment + SqueezerListFragment — converted to Compose, registered via SqueezerNavigation
import eu.darken.sdmse.stats.ui.AffectedFilesRoute
// ReportsFragment — converted to Compose, registered via StatsNavigation
import eu.darken.sdmse.common.navigation.routes.CustomFilterListRoute
// StatsSettingsFragment — converted to Compose, registered via StatsNavigation
// SwiperSettingsFragment — converted to Compose, registered via SwiperNavigation
// SystemCleanerSettingsFragment — converted to Compose, registered via SystemCleanerNavigation
// SystemCleanerListFragment, FilterContentDetailsFragment, FilterContentFragment — converted to Compose, registered via SystemCleanerNavigation
// CustomFilterEditorFragment — converted to Compose, registered via SystemCleanerNavigation
// CustomFilterListFragment — converted to Compose, registered via SystemCleanerNavigation
// UpgradeFragment — converted to Compose
import kotlin.reflect.typeOf

private val StorageIdNavType = serializableNavType(StorageId.serializer())
private val InstallIdNavType = serializableNavType(InstallId.serializer())
private val NullableInstallIdNavType = serializableNavType(InstallId.serializer(), isNullableAllowed = true)
private val ContentGroupIdNavType = serializableNavType(ContentGroup.Id.serializer())
internal val SetupScreenOptionsNavType = serializableNavType(SetupScreenOptions.serializer(), isNullableAllowed = true)

fun NavGraphBuilder.mainNavGraph() {
    // Onboarding — converted to Compose, registered via AppNavigation

    // Dashboard — converted to Compose, registered via AppNavigation

    // Setup — converted to Compose, registered via AppNavigation

    // Settings — converted to Compose, registered via AppNavigation

    // Upgrade
    // Upgrade — converted to Compose, registered via AppNavigation

    // Data Areas & Log
    // DataAreas — converted to Compose, registered via AppNavigation
    // LogView — converted to Compose, registered via AppNavigation

    // Tool Settings (Fragment-based, will be converted in later phases)
    // CorpseFinderSettings — converted to Compose, registered via CorpseFinderNavigation
    // SystemCleanerSettings — converted to Compose, registered via SystemCleanerNavigation
    // AppCleanerSettings — converted to Compose, registered via AppCleanerNavigation
    // DeduplicatorSettings — converted to Compose, registered via DeduplicatorNavigation
    // AppControlSettings — converted to Compose, registered via AppControlNavigation
    // SqueezerSettings — converted to Compose, registered via SqueezerNavigation
    // SwiperSettings — converted to Compose, registered via SwiperNavigation
    // SchedulerSettings — converted to Compose, registered via SchedulerNavigation
    // StatsSettings — converted to Compose, registered via StatsNavigation

    // CorpseFinder — all screens converted to Compose, registered via CorpseFinderNavigation

    // SystemCleaner — list/details/customfilter all converted to Compose, registered via SystemCleanerNavigation

    // AppCleaner — list/details/page converted to Compose, registered via AppCleanerNavigation

    // AppControl — list/action-sheet converted to Compose, registered via AppControlNavigation

    // Scheduler
    fragment<SchedulerManagerFragment, SchedulerManagerRoute>()
    dialog<ScheduleItemDialog, ScheduleItemRoute>()

    // Analyzer
    fragment<DeviceStorageFragment, DeviceStorageRoute>()
    fragment<StorageContentFragment, StorageContentRoute>(
        typeMap = mapOf(typeOf<StorageId>() to StorageIdNavType)
    )
    fragment<AppsFragment, AppsRoute>(
        typeMap = mapOf(typeOf<StorageId>() to StorageIdNavType)
    )
    fragment<AppDetailsFragment, AppDetailsRoute>(
        typeMap = mapOf(
            typeOf<StorageId>() to StorageIdNavType,
            typeOf<InstallId>() to InstallIdNavType,
        )
    )
    fragment<ContentFragment, ContentRoute>(
        typeMap = mapOf(
            typeOf<StorageId>() to StorageIdNavType,
            typeOf<ContentGroup.Id>() to ContentGroupIdNavType,
            typeOf<InstallId?>() to NullableInstallIdNavType,
        )
    )

    // Exclusions — converted to Compose, registered via ExclusionNavigation

    // Deduplicator — all screens converted to Compose, registered via DeduplicatorNavigation

    // Squeezer — all screens converted to Compose, registered via SqueezerNavigation

    // Preview — converted to Compose, registered via PreviewNavigation

    // Reports / Stats — converted to Compose, registered via StatsNavigation

    // Picker — converted to Compose, registered via PickerNavigation

    // Swiper — fully converted to Compose, registered via SwiperNavigation
}
