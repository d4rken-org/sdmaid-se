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
import eu.darken.sdmse.appcleaner.ui.AppCleanerListRoute
import eu.darken.sdmse.appcleaner.ui.AppJunkDetailsRoute
import eu.darken.sdmse.appcleaner.ui.AppJunkRoute
import eu.darken.sdmse.appcleaner.ui.details.AppJunkDetailsFragment
import eu.darken.sdmse.appcleaner.ui.details.appjunk.AppJunkFragment
import eu.darken.sdmse.appcleaner.ui.list.AppCleanerListFragment
import eu.darken.sdmse.appcontrol.ui.AppActionRoute
import eu.darken.sdmse.common.navigation.routes.AppControlListRoute
import eu.darken.sdmse.appcontrol.ui.list.AppControlListFragment
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionDialog
import eu.darken.sdmse.common.debug.logviewer.ui.LogViewFragment
import eu.darken.sdmse.common.navigation.routes.DataAreasRoute
import eu.darken.sdmse.common.navigation.routes.DashboardRoute
import eu.darken.sdmse.common.navigation.routes.LogViewRoute
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.navigation.serializableNavType
import eu.darken.sdmse.common.picker.PickerFragment
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerRoute
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.previews.PreviewFragment
import eu.darken.sdmse.common.previews.PreviewItemRoute
import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.common.previews.PreviewRoute
import eu.darken.sdmse.common.previews.item.PreviewItem
import eu.darken.sdmse.common.previews.item.PreviewItemFragment
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.corpsefinder.ui.CorpseDetailsRoute
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderListRoute
import eu.darken.sdmse.corpsefinder.ui.CorpseRoute
import eu.darken.sdmse.corpsefinder.ui.details.CorpseDetailsFragment
import eu.darken.sdmse.corpsefinder.ui.details.corpse.CorpseFragment
import eu.darken.sdmse.corpsefinder.ui.list.CorpseFinderListFragment
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.ArbiterConfigRoute
import eu.darken.sdmse.deduplicator.ui.ClusterRoute
import eu.darken.sdmse.deduplicator.ui.DeduplicatorDetailsRoute
import eu.darken.sdmse.deduplicator.ui.DeduplicatorListRoute
import eu.darken.sdmse.deduplicator.ui.details.DeduplicatorDetailsFragment
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterFragment
import eu.darken.sdmse.deduplicator.ui.list.DeduplicatorListFragment
import eu.darken.sdmse.deduplicator.ui.settings.arbiter.ArbiterConfigFragment
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.exclusion.ui.PathExclusionEditorRoute
import eu.darken.sdmse.exclusion.ui.PkgExclusionEditorRoute
import eu.darken.sdmse.exclusion.ui.SegmentExclusionEditorRoute
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionEditorOptions
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionFragment
import eu.darken.sdmse.exclusion.ui.editor.pkg.PkgExclusionEditorOptions
import eu.darken.sdmse.exclusion.ui.editor.pkg.PkgExclusionFragment
import eu.darken.sdmse.exclusion.ui.editor.segment.SegmentExclusionEditorOptions
import eu.darken.sdmse.exclusion.ui.editor.segment.SegmentExclusionFragment
import eu.darken.sdmse.exclusion.ui.list.ExclusionListFragment
import eu.darken.sdmse.main.ui.areas.DataAreasFragment
import eu.darken.sdmse.main.ui.dashboard.DashboardFragment
import eu.darken.sdmse.main.ui.onboarding.privacy.OnboardingPrivacyFragment
import eu.darken.sdmse.main.ui.onboarding.setup.OnboardingSetupFragment
import eu.darken.sdmse.main.ui.onboarding.versus.VersusSetupFragment
import eu.darken.sdmse.main.ui.onboarding.welcome.OnboardingWelcomeFragment
import eu.darken.sdmse.main.ui.settings.SettingsFragment
import eu.darken.sdmse.main.ui.settings.cards.DashboardCardConfigFragment
import eu.darken.sdmse.main.ui.settings.support.contactform.SupportContactFormFragment
import eu.darken.sdmse.main.ui.settings.support.sessions.DebugLogSessionsDialog
import eu.darken.sdmse.scheduler.ui.ScheduleItemRoute
import eu.darken.sdmse.scheduler.ui.SchedulerManagerRoute
import eu.darken.sdmse.scheduler.ui.manager.SchedulerManagerFragment
import eu.darken.sdmse.scheduler.ui.manager.create.ScheduleItemDialog
import eu.darken.sdmse.setup.SetupFragment
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenOptions
import eu.darken.sdmse.squeezer.ui.SqueezerListRoute
import eu.darken.sdmse.squeezer.ui.SqueezerSetupRoute
import eu.darken.sdmse.squeezer.ui.SqueezerSettingsRoute
import eu.darken.sdmse.squeezer.ui.list.SqueezerListFragment
import eu.darken.sdmse.squeezer.ui.settings.SqueezerSettingsFragment
import eu.darken.sdmse.squeezer.ui.setup.SqueezerSetupFragment
import eu.darken.sdmse.stats.ui.AffectedFilesRoute
import eu.darken.sdmse.stats.ui.AffectedPkgsRoute
import eu.darken.sdmse.stats.ui.ReportsRoute
import eu.darken.sdmse.stats.ui.SpaceHistoryRoute
import eu.darken.sdmse.stats.ui.paths.AffectedPathsFragment
import eu.darken.sdmse.stats.ui.pkgs.AffectedPkgsFragment
import eu.darken.sdmse.stats.ui.reports.ReportsFragment
import eu.darken.sdmse.stats.ui.spacehistory.SpaceHistoryFragment
import eu.darken.sdmse.common.navigation.routes.SwiperSessionsRoute
import eu.darken.sdmse.swiper.ui.SwiperStatusRoute
import eu.darken.sdmse.swiper.ui.SwiperSwipeRoute
import eu.darken.sdmse.swiper.ui.sessions.SwiperSessionsFragment
import eu.darken.sdmse.swiper.ui.status.SwiperStatusFragment
import eu.darken.sdmse.swiper.ui.swipe.SwiperSwipeFragment
import eu.darken.sdmse.common.filter.CustomFilterEditorOptions
import eu.darken.sdmse.common.filter.CustomFilterEditorRoute
import eu.darken.sdmse.common.navigation.routes.CustomFilterListRoute
import eu.darken.sdmse.systemcleaner.ui.FilterContentDetailsRoute
import eu.darken.sdmse.systemcleaner.ui.FilterContentRoute
import eu.darken.sdmse.systemcleaner.ui.SystemCleanerListRoute
import eu.darken.sdmse.systemcleaner.ui.customfilter.editor.CustomFilterEditorFragment
import eu.darken.sdmse.systemcleaner.ui.customfilter.list.CustomFilterListFragment
import eu.darken.sdmse.systemcleaner.ui.details.FilterContentDetailsFragment
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.FilterContentFragment
import eu.darken.sdmse.systemcleaner.ui.list.SystemCleanerListFragment
import eu.darken.sdmse.common.upgrade.ui.UpgradeFragment
import kotlin.reflect.typeOf

private val StorageIdNavType = serializableNavType(StorageId.serializer())
private val InstallIdNavType = serializableNavType(InstallId.serializer())
private val NullableInstallIdNavType = serializableNavType(InstallId.serializer(), isNullableAllowed = true)
private val ContentGroupIdNavType = serializableNavType(ContentGroup.Id.serializer())
private val DuplicateClusterIdNavType = serializableNavType(Duplicate.Cluster.Id.serializer())
private val NullableDuplicateClusterIdNavType = serializableNavType(Duplicate.Cluster.Id.serializer(), isNullableAllowed = true)
internal val SetupScreenOptionsNavType = serializableNavType(SetupScreenOptions.serializer(), isNullableAllowed = true)
private val PathExclusionEditorOptionsNavType = serializableNavType(PathExclusionEditorOptions.serializer(), isNullableAllowed = true)
private val PkgExclusionEditorOptionsNavType = serializableNavType(PkgExclusionEditorOptions.serializer(), isNullableAllowed = true)
private val SegmentExclusionEditorOptionsNavType = serializableNavType(SegmentExclusionEditorOptions.serializer(), isNullableAllowed = true)
private val CustomFilterEditorOptionsNavType = serializableNavType(CustomFilterEditorOptions.serializer(), isNullableAllowed = true)
private val PreviewOptionsNavType = serializableNavType(PreviewOptions.serializer())
private val PreviewItemNavType = serializableNavType(PreviewItem.serializer())
private val PickerRequestNavType = serializableNavType(PickerRequest.serializer())

fun NavGraphBuilder.mainNavGraph() {
    // Onboarding
    fragment<OnboardingWelcomeFragment, OnboardingWelcomeRoute>()
    fragment<VersusSetupFragment, VersusSetupRoute>()
    fragment<OnboardingPrivacyFragment, OnboardingPrivacyRoute>()
    fragment<OnboardingSetupFragment, OnboardingSetupRoute>()

    // Dashboard
    fragment<DashboardFragment, DashboardRoute>()

    // Setup
    fragment<SetupFragment, SetupRoute>(
        typeMap = mapOf(typeOf<SetupScreenOptions?>() to SetupScreenOptionsNavType)
    )

    // Settings
    fragment<SettingsFragment, SettingsRoute>()
    dialog<DebugLogSessionsDialog, DebugLogSessionsRoute>()
    fragment<SupportContactFormFragment, SupportFormRoute>()
    fragment<DashboardCardConfigFragment, DashboardCardConfigRoute>()

    // Upgrade
    fragment<UpgradeFragment, UpgradeRoute>()

    // Data Areas & Log
    fragment<DataAreasFragment, DataAreasRoute>()
    fragment<LogViewFragment, LogViewRoute>()

    // CorpseFinder
    fragment<CorpseFinderListFragment, CorpseFinderListRoute>()
    fragment<CorpseDetailsFragment, CorpseDetailsRoute>()
    fragment<CorpseFragment, CorpseRoute>()

    // SystemCleaner
    fragment<SystemCleanerListFragment, SystemCleanerListRoute>()
    fragment<FilterContentDetailsFragment, FilterContentDetailsRoute>()
    fragment<FilterContentFragment, FilterContentRoute>()
    fragment<CustomFilterListFragment, CustomFilterListRoute>()
    fragment<CustomFilterEditorFragment, CustomFilterEditorRoute>(
        typeMap = mapOf(typeOf<CustomFilterEditorOptions?>() to CustomFilterEditorOptionsNavType)
    )

    // AppCleaner
    fragment<AppCleanerListFragment, AppCleanerListRoute>()
    fragment<AppJunkDetailsFragment, AppJunkDetailsRoute>(
        typeMap = mapOf(typeOf<InstallId?>() to NullableInstallIdNavType)
    )
    fragment<AppJunkFragment, AppJunkRoute>(
        typeMap = mapOf(typeOf<InstallId>() to InstallIdNavType)
    )

    // AppControl
    fragment<AppControlListFragment, AppControlListRoute>()
    dialog<AppActionDialog, AppActionRoute>(
        typeMap = mapOf(typeOf<InstallId>() to InstallIdNavType)
    )

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

    // Exclusions
    fragment<ExclusionListFragment, ExclusionsListRoute>()
    fragment<PathExclusionFragment, PathExclusionEditorRoute>(
        typeMap = mapOf(typeOf<PathExclusionEditorOptions?>() to PathExclusionEditorOptionsNavType)
    )
    fragment<PkgExclusionFragment, PkgExclusionEditorRoute>(
        typeMap = mapOf(typeOf<PkgExclusionEditorOptions?>() to PkgExclusionEditorOptionsNavType)
    )
    fragment<SegmentExclusionFragment, SegmentExclusionEditorRoute>(
        typeMap = mapOf(typeOf<SegmentExclusionEditorOptions?>() to SegmentExclusionEditorOptionsNavType)
    )

    // Deduplicator
    fragment<DeduplicatorListFragment, DeduplicatorListRoute>()
    fragment<DeduplicatorDetailsFragment, DeduplicatorDetailsRoute>(
        typeMap = mapOf(typeOf<Duplicate.Cluster.Id?>() to NullableDuplicateClusterIdNavType)
    )
    fragment<ClusterFragment, ClusterRoute>(
        typeMap = mapOf(typeOf<Duplicate.Cluster.Id>() to DuplicateClusterIdNavType)
    )
    fragment<ArbiterConfigFragment, ArbiterConfigRoute>()

    // Squeezer
    fragment<SqueezerSetupFragment, SqueezerSetupRoute>()
    fragment<SqueezerListFragment, SqueezerListRoute>()
    fragment<SqueezerSettingsFragment, SqueezerSettingsRoute>()

    // Preview
    dialog<PreviewFragment, PreviewRoute>(
        typeMap = mapOf(typeOf<PreviewOptions>() to PreviewOptionsNavType)
    )
    fragment<PreviewItemFragment, PreviewItemRoute>(
        typeMap = mapOf(typeOf<PreviewItem>() to PreviewItemNavType)
    )

    // Reports / Stats
    fragment<ReportsFragment, ReportsRoute>()
    fragment<SpaceHistoryFragment, SpaceHistoryRoute>()
    fragment<AffectedPathsFragment, AffectedFilesRoute>()
    fragment<AffectedPkgsFragment, AffectedPkgsRoute>()

    // Picker
    fragment<PickerFragment, PickerRoute>(
        typeMap = mapOf(typeOf<PickerRequest>() to PickerRequestNavType)
    )

    // Swiper
    fragment<SwiperSessionsFragment, SwiperSessionsRoute>()
    fragment<SwiperSwipeFragment, SwiperSwipeRoute>()
    fragment<SwiperStatusFragment, SwiperStatusRoute>()
}
