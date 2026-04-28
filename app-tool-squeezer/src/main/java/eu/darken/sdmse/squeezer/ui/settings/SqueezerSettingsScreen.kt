package eu.darken.sdmse.squeezer.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Compress
import androidx.compose.material.icons.twotone.History
import androidx.compose.material.icons.twotone.NewReleases
import androidx.compose.material.icons.twotone.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.compose.settings.SettingsSwitchItem
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.common.R as CommonR

@Composable
fun SqueezerSettingsScreenHost(
    vm: SqueezerSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    SqueezerSettingsScreen(
        state = state,
        onNavigateUp = vm::navUp,
        onIncludeJpegChanged = vm::setIncludeJpeg,
        onIncludeWebpChanged = vm::setIncludeWebp,
        onSkipPreviouslyCompressedChanged = vm::setSkipPreviouslyCompressed,
        onWriteExifMarkerChanged = vm::setWriteExifMarker,
        onClearHistoryConfirmed = vm::clearHistory,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SqueezerSettingsScreen(
    state: SqueezerSettingsViewModel.State = SqueezerSettingsViewModel.State(),
    onNavigateUp: () -> Unit = {},
    onIncludeJpegChanged: (Boolean) -> Unit = {},
    onIncludeWebpChanged: (Boolean) -> Unit = {},
    onSkipPreviouslyCompressedChanged: (Boolean) -> Unit = {},
    onWriteExifMarkerChanged: (Boolean) -> Unit = {},
    onClearHistoryConfirmed: () -> Unit = {},
) {
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val historySummary = if (state.historyCount > 0) {
        val (formatted, _) = ByteFormatter.formatSize(context, state.historyDatabaseSize)
        pluralStringResource(R.plurals.squeezer_history_summary, state.historyCount, state.historyCount, formatted)
    } else {
        stringResource(R.string.squeezer_history_empty)
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.squeezer_history_clear_title)) },
            text = { Text(stringResource(R.string.squeezer_history_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistoryConfirmed()
                    showClearDialog = false
                }) { Text(stringResource(CommonR.string.general_reset_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.squeezer_tool_name)) },
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
            item { SettingsCategoryHeader(text = stringResource(R.string.squeezer_types_category_label)) }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Compress,
                    title = stringResource(R.string.squeezer_type_jpeg_title),
                    subtitle = stringResource(R.string.squeezer_type_jpeg_description),
                    checked = state.includeJpeg,
                    onCheckedChange = onIncludeJpegChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Compress,
                    title = stringResource(R.string.squeezer_type_webp_title),
                    subtitle = stringResource(R.string.squeezer_type_webp_description),
                    checked = state.includeWebp,
                    onCheckedChange = onIncludeWebpChanged,
                )
            }
            item { SettingsCategoryHeader(text = stringResource(R.string.squeezer_compression_settings_label)) }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.RotateRight,
                    title = stringResource(R.string.squeezer_skip_compressed_title),
                    subtitle = stringResource(R.string.squeezer_skip_compressed_description),
                    checked = state.skipPreviouslyCompressed,
                    onCheckedChange = onSkipPreviouslyCompressedChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.NewReleases,
                    title = stringResource(R.string.squeezer_exif_marker_title),
                    subtitle = stringResource(R.string.squeezer_exif_marker_description),
                    checked = state.writeExifMarker,
                    onCheckedChange = onWriteExifMarkerChanged,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.History,
                    title = stringResource(R.string.squeezer_history_title),
                    subtitle = historySummary,
                    onClick = { showClearDialog = true },
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SqueezerSettingsScreenPreview() {
    PreviewWrapper {
        SqueezerSettingsScreen(
            state = SqueezerSettingsViewModel.State(
                includeJpeg = true,
                includeWebp = true,
                skipPreviouslyCompressed = true,
                writeExifMarker = false,
                historyCount = 42,
                historyDatabaseSize = 32 * 1024L,
            ),
        )
    }
}
