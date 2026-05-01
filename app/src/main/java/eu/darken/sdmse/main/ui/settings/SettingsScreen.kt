package eu.darken.sdmse.main.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ViewList
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.Compress
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.Recycling
import androidx.compose.material.icons.twotone.Swipe
import androidx.compose.material.icons.automirrored.twotone.ContactSupport
import androidx.compose.material.icons.twotone.Alarm
import androidx.compose.material.icons.twotone.BarChart
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.icons.Ghost
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.appcleaner.ui.AppCleanerSettingsRoute
import eu.darken.sdmse.appcontrol.ui.AppControlSettingsRoute
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderSettingsRoute
import eu.darken.sdmse.deduplicator.ui.DeduplicatorSettingsRoute
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.main.ui.navigation.AcknowledgementsRoute
import eu.darken.sdmse.main.ui.navigation.GeneralSettingsRoute
import eu.darken.sdmse.main.ui.navigation.SupportRoute
import eu.darken.sdmse.scheduler.ui.SchedulerSettingsRoute
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.squeezer.ui.SqueezerSettingsRoute
import eu.darken.sdmse.stats.ui.StatsSettingsRoute
import eu.darken.sdmse.swiper.ui.SwiperSettingsRoute
import eu.darken.sdmse.systemcleaner.ui.SystemCleanerSettingsRoute
import eu.darken.sdmse.setup.SetupScreenOptions
import kotlinx.coroutines.launch

@Composable
fun SettingsScreenHost(
    vm: SettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state by vm.state.collectAsStateWithLifecycle()

    scope.launch {
        vm.events.collect { event ->
            when (event) {
                is SettingEvents.ShowVersionInfo -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.info,
                        actionLabel = context.getString(CommonR.string.general_copy_action),
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.copyVersionInfos()
                    }
                }
            }
        }
    }

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onExclusionsClick = { vm.navTo(ExclusionsListRoute) },
        onGeneralSettingsClick = { vm.navTo(GeneralSettingsRoute) },
        onSetupClick = { vm.navTo(SetupRoute(options = SetupScreenOptions(showCompleted = true))) },
        onSupportClick = { vm.navTo(SupportRoute) },
        onAcknowledgementsClick = { vm.navTo(AcknowledgementsRoute) },
        onPrivacyClick = vm::openPrivacyPolicy,
        onSponsorClick = vm::openUpgradeWebsite,
        onChangelogClick = { vm.openWebsite("https://sdmse.darken.eu/changelog") },
        onChangelogLongClick = vm::showVersionInfos,
        onToolSettingsClick = { tool ->
            val route = when (tool) {
                "CorpseFinder" -> CorpseFinderSettingsRoute
                "SystemCleaner" -> SystemCleanerSettingsRoute
                "AppCleaner" -> AppCleanerSettingsRoute
                "Deduplicator" -> DeduplicatorSettingsRoute
                "AppControl" -> AppControlSettingsRoute
                "Squeezer" -> SqueezerSettingsRoute
                "Swiper" -> SwiperSettingsRoute
                else -> return@SettingsScreen
            }
            vm.navTo(route)
        },
        onStatsClick = { vm.navTo(StatsSettingsRoute) },
        onSchedulerClick = { vm.navTo(SchedulerSettingsRoute) },
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsViewModel.State = SettingsViewModel.State(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onExclusionsClick: () -> Unit = {},
    onGeneralSettingsClick: () -> Unit = {},
    onSetupClick: () -> Unit = {},
    onSupportClick: () -> Unit = {},
    onAcknowledgementsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onSponsorClick: () -> Unit = {},
    onChangelogClick: () -> Unit = {},
    onChangelogLongClick: () -> Unit = {},
    onToolSettingsClick: (String) -> Unit = {},
    onStatsClick: () -> Unit = {},
    onSchedulerClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.general_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            // Exclusions
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Shield,
                    title = stringResource(eu.darken.sdmse.common.exclusion.R.string.exclusion_manager_title),
                    subtitle = stringResource(eu.darken.sdmse.common.exclusion.R.string.exclusion_manager_desc),
                    onClick = onExclusionsClick,
                )
            }

            // Tools category
            item { SettingsCategoryHeader(text = stringResource(CommonR.string.settings_category_tools_label)) }

            item {
                SettingsPreferenceItem(
                    icon = SdmIcons.Ghost,
                    title = stringResource(CommonR.string.corpsefinder_tool_name),
                    subtitle = stringResource(eu.darken.sdmse.corpsefinder.R.string.corpsefinder_settings_summary),
                    onClick = { onToolSettingsClick("CorpseFinder") },
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.AutoMirrored.TwoTone.ViewList,
                    title = stringResource(CommonR.string.systemcleaner_tool_name),
                    subtitle = stringResource(eu.darken.sdmse.systemcleaner.R.string.systemcleaner_explanation_short),
                    onClick = { onToolSettingsClick("SystemCleaner") },
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Recycling,
                    title = stringResource(CommonR.string.appcleaner_tool_name),
                    subtitle = stringResource(eu.darken.sdmse.appcleaner.R.string.appcleaner_explanation_short),
                    onClick = { onToolSettingsClick("AppCleaner") },
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.ContentCopy,
                    title = stringResource(CommonR.string.deduplicator_tool_name),
                    subtitle = stringResource(eu.darken.sdmse.deduplicator.R.string.deduplicator_explanation_short),
                    onClick = { onToolSettingsClick("Deduplicator") },
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Apps,
                    title = stringResource(CommonR.string.appcontrol_tool_name),
                    subtitle = stringResource(eu.darken.sdmse.appcontrol.R.string.appcontrol_explanation_short),
                    onClick = { onToolSettingsClick("AppControl") },
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Compress,
                    title = stringResource(CommonR.string.squeezer_tool_name),
                    subtitle = stringResource(eu.darken.sdmse.squeezer.R.string.squeezer_explanation_short),
                    onClick = { onToolSettingsClick("Squeezer") },
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Swipe,
                    title = stringResource(CommonR.string.swiper_tool_name),
                    subtitle = stringResource(eu.darken.sdmse.swiper.R.string.swiper_tool_description),
                    onClick = { onToolSettingsClick("Swiper") },
                )
            }

            // Device category
            item { SettingsCategoryHeader(text = stringResource(CommonR.string.general_device_label)) }

            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Settings,
                    title = stringResource(R.string.general_settings_label),
                    subtitle = stringResource(R.string.general_settings_desc),
                    onClick = onGeneralSettingsClick,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.BarChart,
                    title = stringResource(CommonR.string.stats_label),
                    subtitle = stringResource(eu.darken.sdmse.common.stats.R.string.stats_settings_desc),
                    onClick = onStatsClick,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Alarm,
                    title = stringResource(eu.darken.sdmse.scheduler.R.string.scheduler_settings_label),
                    subtitle = stringResource(eu.darken.sdmse.scheduler.R.string.scheduler_settings_summary),
                    onClick = onSchedulerClick,
                )
            }
            item {
                val setupTint: Color? = if (state.setupDone) null else MaterialTheme.colorScheme.tertiary
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.PhoneAndroid,
                    iconTint = setupTint,
                    title = stringResource(CommonR.string.setup_title),
                    subtitle = stringResource(R.string.setup_forcedshow_summary),
                    onClick = onSetupClick,
                )
            }

            // Other category
            item { SettingsCategoryHeader(text = stringResource(R.string.settings_category_other_label)) }

            if (BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.FOSS && !state.isPro) {
                item {
                    SettingsPreferenceItem(
                        icon = Icons.TwoTone.Stars,
                        title = stringResource(R.string.settings_sponsor_development_title),
                        subtitle = stringResource(R.string.settings_sponsor_development_summary),
                        onClick = onSponsorClick,
                    )
                }
            }

            item {
                SettingsPreferenceItem(
                    icon = Icons.AutoMirrored.TwoTone.ContactSupport,
                    title = stringResource(R.string.settings_support_label),
                    subtitle = stringResource(R.string.settings_support_description),
                    onClick = onSupportClick,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Update,
                    title = stringResource(R.string.changelog_label),
                    subtitle = BuildConfigWrap.VERSION_DESCRIPTION,
                    onClick = onChangelogClick,
                    onLongClick = onChangelogLongClick,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Favorite,
                    title = stringResource(R.string.settings_acknowledgements_label),
                    subtitle = stringResource(CommonR.string.general_thank_you_label),
                    onClick = onAcknowledgementsClick,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Policy,
                    title = stringResource(R.string.settings_privacy_policy_label),
                    subtitle = stringResource(R.string.settings_privacy_policy_desc),
                    onClick = onPrivacyClick,
                )
            }
        }
    }
}
