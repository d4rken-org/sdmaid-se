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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.main.ui.dashboard.items.TitleCardVH
import eu.darken.sdmse.main.ui.navigation.SettingsRoute

@Composable
fun DashboardScreenHost(
    vm: DashboardViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val listState by vm.listState.collectAsStateWithLifecycle(initialValue = null)
    val bottomBarState by vm.bottomBarState.collectAsStateWithLifecycle(initialValue = null)

    DashboardScreen(
        listState = listState,
        bottomBarState = bottomBarState,
        onMainAction = { bottomBarState?.actionState?.let { vm.mainAction(it) } },
        onSettings = { vm.navTo(SettingsRoute) },
        onUpgrade = { vm.navTo(UpgradeRoute()) },
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
                    .padding(paddingValues),
            ) {
                items(
                    items = items,
                    key = { it.stableId },
                ) { item ->
                    DashboardToolCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun DashboardToolCard(item: DashboardAdapter.Item) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        when (item) {
            is TitleCardVH.Item -> {
                Text(
                    text = "SD Maid SE",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp),
                )
            }

            else -> {
                Text(
                    text = item::class.simpleName ?: "Unknown card",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
