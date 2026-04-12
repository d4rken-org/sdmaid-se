package eu.darken.sdmse.main.ui.areas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun DataAreasScreenHost(
    vm: DataAreasViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    DataAreasScreen(
        stateSource = vm.state,
        onReload = vm::reloadDataAreas,
        onNavigateUp = vm::navUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DataAreasScreen(
    stateSource: Flow<DataAreasViewModel.State> = flowOf(DataAreasViewModel.State()),
    onReload: () -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    val state = stateSource.collectAsStateWithLifecycle(initialValue = DataAreasViewModel.State())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(eu.darken.sdmse.R.string.data_areas_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.value.allowReload) {
                        IconButton(onClick = onReload) {
                            Icon(Icons.TwoTone.Refresh, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        val areas = state.value.areas
        if (areas == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                items(areas.toList()) { area ->
                    DataAreaRow(area = area)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DataAreaRow(area: DataArea) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.TwoTone.Folder,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = area.type.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = area.path.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
