package eu.darken.sdmse.analyzer.ui.storage.device

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun DeviceStorageScreenHost(
    vm: DeviceStorageViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    DeviceStorageScreen(
        stateSource = vm.state,
        onStorageClick = vm::onStorageClick,
        onTrendClick = vm::onTrendClick,
        onRefresh = vm::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceStorageScreen(
    stateSource: Flow<DeviceStorageViewModel.State> = MutableStateFlow(DeviceStorageViewModel.State()),
    onStorageClick: (DeviceStorageViewModel.Row) -> Unit = {},
    onTrendClick: (DeviceStorageViewModel.Row) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle(initialValue = DeviceStorageViewModel.State())
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analyzer_device_storage_title)) },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = state.progress == null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.storages, key = { it.storage.id.hashCode() }) { row ->
                    DeviceStorageItemCard(
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
