package eu.darken.sdmse.squeezer.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.InsertDriveFile
import androidx.compose.material.icons.twotone.Compress
import androidx.compose.material.icons.twotone.History
import androidx.compose.material.icons.twotone.Movie
import androidx.compose.material.icons.twotone.NewReleases
import androidx.compose.material.icons.twotone.RotateRight
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.text.format.Formatter
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.compose.settings.SettingsSwitchItem
import eu.darken.sdmse.common.compose.settings.dialogs.SizeInputDialog
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.squeezer.core.SqueezerSettings
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
        onIncludeVideoChanged = vm::setIncludeVideo,
        onSkipPreviouslyCompressedChanged = vm::setSkipPreviouslyCompressed,
        onWriteExifMarkerChanged = vm::setWriteExifMarker,
        onMinSizeChanged = vm::setMinSizeBytes,
        onClearHistoryConfirmed = vm::clearHistory,
    )
}

@Composable
internal fun SqueezerSettingsScreen(
    state: SqueezerSettingsViewModel.State = SqueezerSettingsViewModel.State(),
    onNavigateUp: () -> Unit = {},
    onIncludeJpegChanged: (Boolean) -> Unit = {},
    onIncludeWebpChanged: (Boolean) -> Unit = {},
    onIncludeVideoChanged: (Boolean) -> Unit = {},
    onSkipPreviouslyCompressedChanged: (Boolean) -> Unit = {},
    onWriteExifMarkerChanged: (Boolean) -> Unit = {},
    onMinSizeChanged: (Long) -> Unit = {},
    onClearHistoryConfirmed: () -> Unit = {},
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showMinSizeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val historySummary = if (state.historyCount > 0) {
        val (formatted, _) = ByteFormatter.formatSize(context, state.historyDatabaseSize)
        pluralStringResource(R.plurals.squeezer_history_summary, state.historyCount, state.historyCount, formatted)
    } else {
        stringResource(R.string.squeezer_history_empty)
    }

    if (showClearDialog) {
        SdmConfirmDialog(
            title = stringResource(R.string.squeezer_history_clear_title),
            message = stringResource(R.string.squeezer_history_clear_message),
            onDismissRequest = { showClearDialog = false },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_reset_action),
                onClick = {
                    onClearHistoryConfirmed()
                    showClearDialog = false
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = { showClearDialog = false },
            ),
        )
    }

    if (showMinSizeDialog) {
        SizeInputDialog(
            titleRes = R.string.squeezer_min_size_title,
            currentSize = state.minSizeBytes,
            maximumSize = 20 * 1000 * 1000L,
            onSave = {
                onMinSizeChanged(it)
                showMinSizeDialog = false
            },
            onReset = {
                onMinSizeChanged(SqueezerSettings.MIN_FILE_SIZE)
                showMinSizeDialog = false
            },
            onDismiss = { showMinSizeDialog = false },
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
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.NewReleases,
                    title = stringResource(R.string.squeezer_exif_marker_title),
                    subtitle = stringResource(R.string.squeezer_exif_marker_description),
                    checked = state.writeExifMarker,
                    onCheckedChange = onWriteExifMarkerChanged,
                )
            }
            item { SettingsCategoryHeader(text = stringResource(R.string.squeezer_video_settings_category_label)) }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Movie,
                    title = stringResource(R.string.squeezer_type_video_title),
                    subtitle = stringResource(R.string.squeezer_type_video_description),
                    checked = state.includeVideo,
                    onCheckedChange = onIncludeVideoChanged,
                )
            }
            item { SettingsCategoryHeader(text = stringResource(R.string.squeezer_compression_settings_label)) }
            item {
                SettingsPreferenceItem(
                    icon = Icons.AutoMirrored.TwoTone.InsertDriveFile,
                    title = stringResource(R.string.squeezer_min_size_title),
                    subtitle = stringResource(R.string.squeezer_min_size_description) +
                        "\n" + Formatter.formatShortFileSize(context, state.minSizeBytes),
                    onClick = { showMinSizeDialog = true },
                )
            }
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
                includeVideo = false,
                skipPreviouslyCompressed = true,
                writeExifMarker = false,
                minSizeBytes = 512 * 1024L,
                historyCount = 42,
                historyDatabaseSize = 32 * 1024L,
            ),
        )
    }
}
