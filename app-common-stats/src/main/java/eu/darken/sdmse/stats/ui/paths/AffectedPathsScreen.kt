package eu.darken.sdmse.stats.ui.paths

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.stats.R
import eu.darken.sdmse.stats.ui.AffectedFilesRoute
import eu.darken.sdmse.stats.ui.paths.items.AffectedPathRow
import eu.darken.sdmse.stats.ui.paths.items.AffectedPathsHeaderItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AffectedPathsScreenHost(
    route: AffectedFilesRoute,
    vm: AffectedPathsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route.reportIdUUID) { vm.setReportId(route.reportIdUUID) }

    AffectedPathsScreen(
        stateSource = vm.state,
        onNavigateUp = vm::navUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AffectedPathsScreen(
    stateSource: StateFlow<AffectedPathsViewModel.State> = MutableStateFlow(AffectedPathsViewModel.State.Loading),
    onNavigateUp: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(CommonR.string.stats_label))
                        Text(
                            text = stringResource(R.string.stats_affected_paths_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val s = state) {
            AffectedPathsViewModel.State.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            AffectedPathsViewModel.State.NotFound -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(CommonR.string.general_empty_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is AffectedPathsViewModel.State.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
            ) {
                item(key = "header") {
                    AffectedPathsHeaderItem(
                        report = s.report,
                        rowCount = s.rows.size,
                    )
                }
                items(s.rows, key = { it.affectedPath.path.path }) { row ->
                    AffectedPathRow(row = row)
                }
            }
        }
    }
}

@Preview2
@Composable
private fun AffectedPathsScreenLoadingPreview() {
    PreviewWrapper {
        AffectedPathsScreen(
            stateSource = MutableStateFlow(AffectedPathsViewModel.State.Loading),
        )
    }
}

@Preview2
@Composable
private fun AffectedPathsScreenNotFoundPreview() {
    PreviewWrapper {
        AffectedPathsScreen(
            stateSource = MutableStateFlow(AffectedPathsViewModel.State.NotFound),
        )
    }
}
