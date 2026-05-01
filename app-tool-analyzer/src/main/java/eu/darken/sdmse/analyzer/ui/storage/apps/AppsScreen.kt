package eu.darken.sdmse.analyzer.ui.storage.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.ui.AppsRoute
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun AppsScreenHost(
    route: AppsRoute,
    vm: AppsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route) { vm.bindRoute(route) }

    AppsScreen(
        stateSource = vm.state,
        onAppClick = vm::onAppClick,
        onUpdateSearchQuery = vm::updateSearchQuery,
        onNavigateUp = vm::navUp,
    )
}

@Composable
internal fun AppsScreen(
    stateSource: Flow<AppsViewModel.State> = MutableStateFlow(AppsViewModel.State()),
    onAppClick: (AppsViewModel.Row) -> Unit = {},
    onUpdateSearchQuery: (String) -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle(initialValue = AppsViewModel.State())
    val context = LocalContext.current

    var searchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    BackHandler(enabled = searchActive) {
        searchText = ""
        onUpdateSearchQuery("")
        searchActive = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = {
                                searchText = it
                                onUpdateSearchQuery(it)
                            },
                            placeholder = { Text(stringResource(CommonR.string.general_search_action)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Column {
                            Text(stringResource(R.string.analyzer_storage_content_title))
                            val subtitle = if (state.isSearchActive) {
                                pluralStringResource(CommonR.plurals.result_x_items, state.apps.size, state.apps.size)
                            } else {
                                state.storage?.label?.get(context) ?: ""
                            }
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (searchActive) {
                        IconButton(onClick = {
                            searchText = ""
                            onUpdateSearchQuery("")
                            searchActive = false
                        }) {
                            Icon(Icons.TwoTone.Close, contentDescription = null)
                        }
                    } else {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(
                                Icons.TwoTone.Search,
                                contentDescription = stringResource(CommonR.string.general_search_action),
                            )
                        }
                    }
                },
            )
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
                items(state.apps, key = { it.pkgStat.id.hashCode() }) { row ->
                    AppsItemRow(
                        row = row,
                        onClick = { onAppClick(row) },
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun AppsScreenEmptyPreview() {
    PreviewWrapper {
        AppsScreen()
    }
}
