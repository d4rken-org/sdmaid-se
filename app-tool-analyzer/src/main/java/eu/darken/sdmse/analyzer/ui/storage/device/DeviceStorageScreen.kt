package eu.darken.sdmse.analyzer.ui.storage.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.ui.storage.device.tour.AnalyzerStorageTour
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.layout.ScrollAwareFab
import eu.darken.sdmse.common.compose.layout.SdmListDefaults
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.layout.plusBottom
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.compose.tour.guidedTourTarget
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun DeviceStorageScreenHost(
    vm: DeviceStorageViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    DeviceStorageScreen(
        stateSource = vm.state,
        onNavigateUp = vm::navUp,
        onStorageClick = vm::onStorageClick,
        onTrendClick = vm::onTrendClick,
        onRefresh = vm::refresh,
    )
}

@Composable
internal fun DeviceStorageScreen(
    stateSource: StateFlow<DeviceStorageViewModel.State> = MutableStateFlow(DeviceStorageViewModel.State()),
    onNavigateUp: () -> Unit = {},
    onStorageClick: (DeviceStorageViewModel.Row) -> Unit = {},
    onTrendClick: (DeviceStorageViewModel.Row) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val gridState = rememberLazyGridState()

    val tourController = LocalGuidedTourController.current
    val tourDef = remember { AnalyzerStorageTour.definition() }
    // Rows can oscillate (scan → refresh → rescan); only attempt the tour once per composition.
    var tourStartAttempted by remember { mutableStateOf(false) }
    // Anchor step 2 on the first storage card. A finished scan always yields at least one storage.
    val tourReady = state.progress == null && state.storages.isNotEmpty()
    LaunchedEffect(tourReady) {
        if (!tourReady || tourStartAttempted) return@LaunchedEffect
        // shouldStart() is false for both "done/dismissed" and "another tour active"; mark attempted
        // only after it passes so a transient block can't permanently suppress this tour.
        if (!tourController.shouldStart(tourDef)) return@LaunchedEffect
        tourStartAttempted = true
        tourController.start(tourDef)
    }

    SdmScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(CommonR.string.analyzer_tool_name))
                        Text(
                            text = stringResource(R.string.analyzer_device_storage_title),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(CommonR.string.general_navigate_up_action),
                        onClick = onNavigateUp,
                    )
                },
            )
        },
        floatingActionButton = {
            ScrollAwareFab(scrollState = gridState, visible = state.progress == null) {
                FloatingActionButton(onClick = onRefresh) {
                    Icon(
                        Icons.TwoTone.Refresh,
                        contentDescription = stringResource(eu.darken.sdmse.common.R.string.general_refresh_action),
                    )
                }
            }
        },
    ) { paddingValues ->
        ProgressOverlay(
            data = state.progress,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(context.getSpanCount(widthDp = 360)),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    .plusBottom(SdmListDefaults.FabClearance),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.storages, key = { _, it -> it.storage.id.hashCode() }) { index, row ->
                    DeviceStorageItemCard(
                        modifier = if (index == 0) {
                            Modifier.guidedTourTarget(AnalyzerStorageTour.STORAGE_CARD_TARGET)
                        } else {
                            Modifier
                        },
                        row = row,
                        onClick = { onStorageClick(row) },
                        onTrendClick = { onTrendClick(row) },
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun DeviceStorageScreenEmptyPreview() {
    PreviewWrapper {
        DeviceStorageScreen()
    }
}
