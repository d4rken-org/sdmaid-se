package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CleaningServices
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Stop
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.sdmse.common.navigation.LegacyNavigationBridge
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.main.ui.dashboard.items.TitleCardVH
import eu.darken.sdmse.main.ui.navigation.SettingsRoute

@Composable
fun DashboardScreenHost(
    @Suppress("DEPRECATION")
    vm: DashboardViewModel = hiltViewModel(),
) {
    // Bridge old ViewModel3 navigation events to Compose NavigationController
    @Suppress("DEPRECATION")
    LegacyNavigationBridge(vm.navEvents)

    // Bridge old ViewModel3 LiveData errors to Compose
    @Suppress("DEPRECATION")
    val error by vm.errorEvents.observeAsState()
    // Error is handled by old ViewModel3 pattern — will migrate to ViewModel4 later

    @Suppress("DEPRECATION")
    val listState by vm.listState.observeAsState()
    @Suppress("DEPRECATION")
    val bottomBarState by vm.bottomBarState.observeAsState()

    DashboardScreen(
        listState = listState,
        bottomBarState = bottomBarState,
        onMainAction = { bottomBarState?.actionState?.let { vm.mainAction(it) } },
        onSettings = { vm.navigateTo(SettingsRoute) },
        onUpgrade = { vm.navigateTo(UpgradeRoute()) },
    )
}

@Composable
internal fun DashboardScreen(
    listState: DashboardViewModel.ListState? = null,
    bottomBarState: DashboardViewModel.BottomBarState? = null,
    onMainAction: () -> Unit = {},
    onSettings: () -> Unit = {},
    onUpgrade: () -> Unit = {},
) {
    Scaffold(
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.TwoTone.Settings, contentDescription = "Settings")
                    }
                },
                floatingActionButton = {
                    val actionState = bottomBarState?.actionState
                    if (actionState != null) {
                        FloatingActionButton(onClick = onMainAction) {
                            when (actionState) {
                                DashboardViewModel.BottomBarState.Action.SCAN -> Icon(
                                    Icons.TwoTone.PlayArrow,
                                    contentDescription = "Scan",
                                )

                                DashboardViewModel.BottomBarState.Action.DELETE -> Icon(
                                    Icons.TwoTone.DeleteSweep,
                                    contentDescription = "Delete",
                                )

                                DashboardViewModel.BottomBarState.Action.ONECLICK -> Icon(
                                    Icons.TwoTone.CleaningServices,
                                    contentDescription = "One-click",
                                )

                                DashboardViewModel.BottomBarState.Action.WORKING -> CircularProgressIndicator()
                                DashboardViewModel.BottomBarState.Action.WORKING_CANCELABLE -> Icon(
                                    Icons.TwoTone.Stop,
                                    contentDescription = "Cancel",
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        val items = listState?.items
        if (items == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp),
            ) {
                items(items, key = { it.stableId }) { item ->
                    DashboardItemCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun DashboardItemCard(item: DashboardAdapter.Item) {
    Card(
        modifier = Modifier.padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        when (item) {
            is TitleCardVH.Item -> {
                Text(
                    text = "SD Maid SE",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }

            is DashboardToolCard.Item -> {
                DashboardToolCardRow(item = item)
            }

            else -> {
                // Placeholder for unimplemented card types
                Text(
                    text = item::class.simpleName ?: "Unknown card",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DashboardToolCardRow(item: DashboardToolCard.Item) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(16.dp),
    ) {
        Text(
            text = item.toolType.name,
            style = MaterialTheme.typography.titleMedium,
        )
        if (item.progress != null) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
        }
        if (item.result != null) {
            Text(
                text = "Last result available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
