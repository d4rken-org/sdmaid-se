package eu.darken.sdmse.analyzer.ui.storage.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.ui.AppDetailsRoute
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsGroupRow
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsHeaderCard
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun AppDetailsScreenHost(
    route: AppDetailsRoute,
    vm: AppDetailsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route) { vm.bindRoute(route) }

    AppDetailsScreen(
        stateSource = vm.state,
        onSettingsClick = vm::onSettingsClick,
        onGroupClick = vm::onGroupClick,
        onRefresh = vm::refresh,
        onNavigateUp = vm::navUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDetailsScreen(
    stateSource: Flow<AppDetailsViewModel.State> = MutableStateFlow(AppDetailsViewModel.State.Loading),
    onSettingsClick: (eu.darken.sdmse.analyzer.core.storage.categories.AppCategory.PkgStat) -> Unit = {},
    onGroupClick: (eu.darken.sdmse.analyzer.core.content.ContentGroup, eu.darken.sdmse.analyzer.core.storage.categories.AppCategory.PkgStat) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle(initialValue = AppDetailsViewModel.State.Loading)
    val context = LocalContext.current

    when (val s = state) {
        AppDetailsViewModel.State.NotFound -> {
            LaunchedEffect(Unit) { onNavigateUp() }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = onNavigateUp) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        },
                    )
                },
            ) { _ -> }
        }

        AppDetailsViewModel.State.Loading -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = onNavigateUp) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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

        is AppDetailsViewModel.State.Ready -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(s.pkgStat.label.get(context))
                                Text(
                                    text = s.storage.label.get(context),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateUp) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        },
                        actions = {
                            if (s.progress == null) {
                                IconButton(onClick = onRefresh) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                }
                            }
                        },
                    )
                },
            ) { paddingValues ->
                ProgressOverlay(
                    data = s.progress,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item("header") {
                            AppDetailsHeaderCard(
                                storage = s.storage,
                                pkgStat = s.pkgStat,
                                onSettingsClick = { onSettingsClick(s.pkgStat) },
                            )
                        }
                        s.appCode?.let { group ->
                            item("appcode") {
                                AppDetailsGroupRow(
                                    group = group,
                                    labelRes = R.string.analyzer_storage_content_app_code_label,
                                    descRes = R.string.analyzer_storage_content_app_code_description,
                                    onClick = { onGroupClick(group, s.pkgStat) },
                                )
                            }
                        }
                        s.appData?.let { group ->
                            item("appdata") {
                                AppDetailsGroupRow(
                                    group = group,
                                    labelRes = R.string.analyzer_storage_content_app_data_label,
                                    descRes = R.string.analyzer_storage_content_app_data_description,
                                    onClick = { onGroupClick(group, s.pkgStat) },
                                )
                            }
                        }
                        s.appMedia?.let { group ->
                            item("appmedia") {
                                AppDetailsGroupRow(
                                    group = group,
                                    labelRes = R.string.analyzer_storage_content_app_media_label,
                                    descRes = R.string.analyzer_storage_content_app_media_description,
                                    onClick = { onGroupClick(group, s.pkgStat) },
                                )
                            }
                        }
                        s.extraData?.let { group ->
                            item("extradata") {
                                AppDetailsGroupRow(
                                    group = group,
                                    labelRes = R.string.analyzer_storage_content_app_extra_label,
                                    descRes = R.string.analyzer_storage_content_app_extra_description,
                                    onClick = { onGroupClick(group, s.pkgStat) },
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
private fun AppDetailsScreenLoadingPreview() {
    PreviewWrapper {
        AppDetailsScreen()
    }
}
