package eu.darken.sdmse.stats.ui.pkgs

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
import eu.darken.sdmse.stats.ui.AffectedPkgsRoute
import eu.darken.sdmse.stats.ui.pkgs.items.AffectedPkgRow
import eu.darken.sdmse.stats.ui.pkgs.items.AffectedPkgsHeaderItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AffectedPkgsScreenHost(
    route: AffectedPkgsRoute,
    vm: AffectedPkgsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route.reportIdUUID) { vm.setReportId(route.reportIdUUID) }

    AffectedPkgsScreen(
        stateSource = vm.state,
        onNavigateUp = vm::navUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AffectedPkgsScreen(
    stateSource: StateFlow<AffectedPkgsViewModel.State> = MutableStateFlow(AffectedPkgsViewModel.State.Loading),
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
                            text = stringResource(R.string.stats_affected_pkgs_label),
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
            AffectedPkgsViewModel.State.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            AffectedPkgsViewModel.State.NotFound -> Box(
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

            is AffectedPkgsViewModel.State.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
            ) {
                item(key = "header") {
                    AffectedPkgsHeaderItem(
                        report = s.report,
                        rowCount = s.rows.size,
                    )
                }
                items(s.rows, key = { it.affectedPkg.pkgId.name + ":" + it.affectedPkg.action.name }) { row ->
                    AffectedPkgRow(row = row)
                }
            }
        }
    }
}

@Preview2
@Composable
private fun AffectedPkgsScreenLoadingPreview() {
    PreviewWrapper {
        AffectedPkgsScreen(
            stateSource = MutableStateFlow(AffectedPkgsViewModel.State.Loading),
        )
    }
}

@Preview2
@Composable
private fun AffectedPkgsScreenNotFoundPreview() {
    PreviewWrapper {
        AffectedPkgsScreen(
            stateSource = MutableStateFlow(AffectedPkgsViewModel.State.NotFound),
        )
    }
}
