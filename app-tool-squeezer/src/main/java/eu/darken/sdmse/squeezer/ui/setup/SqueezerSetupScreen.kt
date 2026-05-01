package eu.darken.sdmse.squeezer.ui.setup

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.InsertDriveFile
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.FolderOpen
import androidx.compose.material.icons.twotone.History
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.compose.settings.dialogs.AgeInputDialog
import eu.darken.sdmse.common.compose.settings.dialogs.SizeInputDialog
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.ui.comparison.SqueezerComparisonDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration

@Composable
fun SqueezerSetupScreenHost(
    vm: SqueezerSetupViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    var exampleDialog by remember { mutableStateOf<SqueezerSetupViewModel.Event.ShowExample?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SqueezerSetupViewModel.Event.ShowExample -> exampleDialog = event
                is SqueezerSetupViewModel.Event.NoExampleFound -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.squeezer_no_example_found),
                        duration = SnackbarDuration.Short,
                    )
                }

                is SqueezerSetupViewModel.Event.NoResultsFound -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.squeezer_result_empty_message),
                        duration = SnackbarDuration.Short,
                    )
                }

                is SqueezerSetupViewModel.Event.PathsDropped -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(
                            R.plurals.squeezer_setup_path_dropped_message,
                            event.droppedPaths.size,
                            event.droppedPaths.size,
                        ),
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        }
    }

    SqueezerSetupScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onPathsClick = vm::openPathPicker,
        onQualityChange = vm::updateQuality,
        onShowExample = vm::showExample,
        onMinAgeChange = vm::updateMinAge,
        onStartScan = vm::startScan,
    )

    exampleDialog?.let { event ->
        SqueezerComparisonDialog(
            media = event.sampleImage,
            quality = event.quality,
            onClose = { exampleDialog = null },
        )
    }
}

@Composable
internal fun SqueezerSetupScreen(
    stateSource: StateFlow<SqueezerSetupViewModel.State> = MutableStateFlow(SqueezerSetupViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onPathsClick: () -> Unit = {},
    onQualityChange: (Int) -> Unit = {},
    onShowExample: () -> Unit = {},
    onMinAgeChange: (Duration) -> Unit = {},
    onStartScan: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    var showAgeDialog by remember { mutableStateOf(false) }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            ProgressOverlay(
                data = state.progress,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    ExplanationCard()
                    Spacer(Modifier.height(16.dp))
                    PathsCard(
                        scanPaths = state.scanPaths,
                        onClick = onPathsClick,
                    )
                    Spacer(Modifier.height(16.dp))
                    QualityCard(
                        quality = state.quality,
                        estimatedSavingsPercent = state.estimatedSavingsPercent,
                        isLoadingExample = state.isLoadingExample,
                        onQualityChange = onQualityChange,
                        onShowExample = onShowExample,
                    )
                    Spacer(Modifier.height(16.dp))
                    AgeCard(
                        minAge = state.minAge,
                        onClick = { showAgeDialog = true },
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onStartScan,
                        enabled = state.canStartScan,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Layers,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.squeezer_setup_start_action))
                    }
                }
            }
        }
    }

    if (showAgeDialog) {
        AgeInputDialog(
            titleRes = R.string.squeezer_min_age_title,
            currentAge = state.minAge,
            maximumAge = Duration.ofDays(365),
            onSave = {
                onMinAgeChange(it)
                showAgeDialog = false
            },
            onReset = {
                onMinAgeChange(SqueezerSettings.MIN_AGE_DEFAULT)
                showAgeDialog = false
            },
            onDismiss = { showAgeDialog = false },
        )
    }
}

@Composable
private fun ExplanationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.TwoTone.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.squeezer_setup_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun PathsCard(
    scanPaths: List<APath>,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val value = if (scanPaths.isEmpty()) {
        stringResource(R.string.squeezer_setup_paths_default)
    } else {
        scanPaths.joinToString("\n") { it.userReadablePath.get(context) }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.TwoTone.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.squeezer_setup_paths_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.TwoTone.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QualityCard(
    quality: Int,
    estimatedSavingsPercent: Int?,
    isLoadingExample: Boolean,
    onQualityChange: (Int) -> Unit,
    onShowExample: () -> Unit,
) {
    val minQuality = if (BuildConfigWrap.DEBUG) 1 else 20
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.squeezer_setup_quality_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.squeezer_quality_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = quality.toFloat(),
                    valueRange = minQuality.toFloat()..100f,
                    steps = 100 - minQuality - 1,
                    onValueChange = { onQualityChange(it.toInt()) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$quality%",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val hintRes = when {
                quality < 40 -> R.string.squeezer_quality_hint_very_low
                quality <= 50 -> R.string.squeezer_quality_hint_low
                quality >= 95 -> R.string.squeezer_quality_hint_high
                else -> R.string.squeezer_quality_hint_normal
            }
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.labelSmall,
                color = if (quality < 40) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (estimatedSavingsPercent != null && quality < 100) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.squeezer_estimated_savings_percent, estimatedSavingsPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    OutlinedButton(
                        onClick = onShowExample,
                        enabled = !isLoadingExample,
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(CommonR.string.general_details_label))
                    }
                    if (isLoadingExample) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgeCard(
    minAge: Duration,
    onClick: () -> Unit,
) {
    val days = minAge.toDays().toInt()
    val valueText = pluralStringResource(R.plurals.squeezer_min_age_x_days, days, days)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.TwoTone.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.squeezer_setup_age_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.squeezer_min_age_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.TwoTone.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview2
@Composable
private fun SqueezerSetupScreenEmptyPreview() {
    PreviewWrapper {
        SqueezerSetupScreen(
            stateSource = MutableStateFlow(SqueezerSetupViewModel.State()),
        )
    }
}
