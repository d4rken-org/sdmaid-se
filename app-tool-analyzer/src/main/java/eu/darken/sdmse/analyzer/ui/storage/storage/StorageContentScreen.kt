package eu.darken.sdmse.analyzer.ui.storage.storage

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.ui.StorageContentRoute
import eu.darken.sdmse.analyzer.ui.storage.storage.categories.AppCategoryCard
import eu.darken.sdmse.analyzer.ui.storage.storage.categories.MediaCategoryCard
import eu.darken.sdmse.analyzer.ui.storage.storage.categories.SystemCategoryCard
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun StorageContentScreenHost(
    route: StorageContentRoute,
    vm: StorageContentViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route) { vm.bindRoute(route) }

    StorageContentScreen(
        stateSource = vm.state,
        onCategoryClick = vm::onCategoryClick,
        onNavigateBack = vm::onNavigateBack,
        onRefresh = vm::refresh,
    )
}

@Composable
internal fun StorageContentScreen(
    stateSource: Flow<StorageContentViewModel.State> = MutableStateFlow(StorageContentViewModel.State.Loading),
    onCategoryClick: (StorageContentViewModel.Row) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle(initialValue = StorageContentViewModel.State.Loading)
    val context = LocalContext.current

    BackHandler(enabled = true) { onNavigateBack() }

    when (val s = state) {
        StorageContentViewModel.State.NotFound -> {
            LaunchedEffect(Unit) { onNavigateBack() }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.analyzer_storage_content_title)) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                            }
                        },
                    )
                },
            ) { _ -> }
        }

        StorageContentViewModel.State.Loading -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.analyzer_storage_content_title)) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                            }
                        },
                    )
                },
            ) { paddingValues ->
                ProgressOverlay(
                    data = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) { }
            }
        }

        is StorageContentViewModel.State.Ready -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.analyzer_storage_content_title)) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                            }
                        },
                    )
                },
                floatingActionButton = {
                    AnimatedVisibility(
                        visible = s.progress == null,
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
                    data = s.progress,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(context.getSpanCount()),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(s.rows.orEmpty(), key = { it::class.java.name }) { row ->
                            when (row) {
                                is StorageContentViewModel.Row.Apps -> AppCategoryCard(
                                    row = row,
                                    onClick = { onCategoryClick(row) },
                                )
                                is StorageContentViewModel.Row.Media -> MediaCategoryCard(
                                    row = row,
                                    onClick = { onCategoryClick(row) },
                                )
                                is StorageContentViewModel.Row.System -> SystemCategoryCard(
                                    row = row,
                                    onClick = { onCategoryClick(row) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview2
@Composable
private fun StorageContentScreenLoadingPreview() {
    PreviewWrapper {
        StorageContentScreen()
    }
}
