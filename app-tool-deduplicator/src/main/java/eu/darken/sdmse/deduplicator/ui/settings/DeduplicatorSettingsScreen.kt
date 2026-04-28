package eu.darken.sdmse.deduplicator.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.InsertDriveFile
import androidx.compose.material.icons.twotone.FolderOpen
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.PlayCircleOutline
import androidx.compose.material.icons.twotone.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.compose.icons.ApproximatelyEqualBox
import eu.darken.sdmse.common.compose.icons.CodeEqualBox
import eu.darken.sdmse.common.compose.icons.Numeric0Box
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.compose.settings.SettingsSwitchItem
import eu.darken.sdmse.common.compose.settings.dialogs.SizeInputDialog
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.deduplicator.R
import eu.darken.sdmse.common.R as CommonR

@Composable
fun DeduplicatorSettingsScreenHost(
    vm: DeduplicatorSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    DeduplicatorSettingsScreen(
        state = state,
        onNavigateUp = vm::navUp,
        onSearchLocationsClick = vm::onSearchLocationsClick,
        onSearchLocationsLongClick = vm::resetScanPaths,
        onArbiterConfigClick = vm::onArbiterConfigClick,
        onAllowDeleteAllChanged = vm::setAllowDeleteAll,
        onMinSizeSaved = vm::setMinSizeBytes,
        onMinSizeReset = vm::resetMinSizeBytes,
        onSkipUncommonChanged = vm::setSkipUncommon,
        onSleuthChecksumChanged = vm::setSleuthChecksumEnabled,
        onSleuthPHashChanged = vm::setSleuthPHashEnabled,
        onSleuthMediaChanged = vm::setSleuthMediaEnabled,
    )
}

@Composable
internal fun DeduplicatorSettingsScreen(
    state: DeduplicatorSettingsViewModel.State = DeduplicatorSettingsViewModel.State(),
    onNavigateUp: () -> Unit = {},
    onSearchLocationsClick: () -> Unit = {},
    onSearchLocationsLongClick: () -> Unit = {},
    onArbiterConfigClick: () -> Unit = {},
    onAllowDeleteAllChanged: (Boolean) -> Unit = {},
    onMinSizeSaved: (Long) -> Unit = {},
    onMinSizeReset: () -> Unit = {},
    onSkipUncommonChanged: (Boolean) -> Unit = {},
    onSleuthChecksumChanged: (Boolean) -> Unit = {},
    onSleuthPHashChanged: (Boolean) -> Unit = {},
    onSleuthMediaChanged: (Boolean) -> Unit = {},
) {
    var showSizeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showSizeDialog) {
        SizeInputDialog(
            titleRes = R.string.deduplicator_skip_minsize_title,
            currentSize = state.minSizeBytes,
            onSave = {
                onMinSizeSaved(it)
                showSizeDialog = false
            },
            onReset = {
                onMinSizeReset()
                showSizeDialog = false
            },
            onDismiss = { showSizeDialog = false },
        )
    }

    val scanPathsSummary = if (state.scanPaths.isEmpty()) {
        stringResource(R.string.deduplicator_search_locations_all_summary)
    } else {
        state.scanPaths.joinToString("\n") { it.userReadablePath.get(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.deduplicator_tool_name)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.FolderOpen,
                    title = stringResource(R.string.deduplicator_search_locations_title),
                    subtitle = scanPathsSummary,
                    onClick = onSearchLocationsClick,
                    onLongClick = onSearchLocationsLongClick,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Psychology,
                    title = stringResource(R.string.deduplicator_arbiter_title),
                    subtitle = stringResource(R.string.deduplicator_arbiter_summary),
                    onClick = onArbiterConfigClick,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = SdmIcons.Numeric0Box,
                    title = stringResource(R.string.deduplicator_protection_deleteall_allowed_title),
                    subtitle = stringResource(R.string.deduplicator_protection_deleteall_allowed_summary),
                    checked = state.allowDeleteAll,
                    onCheckedChange = onAllowDeleteAllChanged,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.AutoMirrored.TwoTone.InsertDriveFile,
                    title = stringResource(R.string.deduplicator_skip_minsize_title),
                    subtitle = stringResource(R.string.deduplicator_skip_minsize_description),
                    value = Formatter.formatShortFileSize(context, state.minSizeBytes),
                    onClick = { showSizeDialog = true },
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.PlayCircleOutline,
                    title = stringResource(R.string.deduplicator_skip_uncommon_title),
                    subtitle = stringResource(R.string.deduplicator_skip_uncommon_description),
                    checked = state.skipUncommon,
                    onCheckedChange = onSkipUncommonChanged,
                )
            }

            item { SettingsCategoryHeader(text = stringResource(R.string.deduplicator_detection_method_label)) }
            item {
                SettingsSwitchItem(
                    icon = SdmIcons.CodeEqualBox,
                    title = stringResource(R.string.deduplicator_detection_method_checksum_title),
                    subtitle = stringResource(R.string.deduplicator_detection_method_checksum_summary),
                    checked = state.isSleuthChecksumEnabled,
                    onCheckedChange = onSleuthChecksumChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = SdmIcons.ApproximatelyEqualBox,
                    title = stringResource(R.string.deduplicator_detection_method_phash_title),
                    subtitle = stringResource(R.string.deduplicator_detection_method_phash_summary),
                    checked = state.isSleuthPHashEnabled,
                    onCheckedChange = onSleuthPHashChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.GraphicEq,
                    title = stringResource(R.string.deduplicator_detection_method_media_title),
                    subtitle = stringResource(R.string.deduplicator_detection_method_media_summary),
                    checked = state.isSleuthMediaEnabled,
                    onCheckedChange = onSleuthMediaChanged,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun DeduplicatorSettingsScreenPreview() {
    PreviewWrapper {
        DeduplicatorSettingsScreen(
            state = DeduplicatorSettingsViewModel.State(
                allowDeleteAll = false,
                minSizeBytes = 2 * 1024L,
                skipUncommon = true,
                isSleuthChecksumEnabled = true,
                isSleuthPHashEnabled = false,
                isSleuthMediaEnabled = false,
            ),
        )
    }
}
