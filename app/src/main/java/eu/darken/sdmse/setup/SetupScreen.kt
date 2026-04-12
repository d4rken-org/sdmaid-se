package eu.darken.sdmse.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.sdmse.common.navigation.LegacyNavigationBridge
import eu.darken.sdmse.common.R as CommonR

@Composable
fun SetupScreenHost(
    @Suppress("DEPRECATION")
    vm: SetupViewModel = hiltViewModel(),
) {
    @Suppress("DEPRECATION")
    LegacyNavigationBridge(vm.navEvents)

    @Suppress("DEPRECATION")
    val listItems by vm.listItems.observeAsState(emptyList())
    @Suppress("DEPRECATION")
    val isSetupComplete by vm.isSetupComplete.observeAsState(false)

    SetupScreen(
        items = listItems,
        isComplete = isSetupComplete,
        isOnboarding = vm.screenOptions.isOnboarding,
        onNavigateBack = { vm.navback() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupScreen(
    items: List<SetupAdapter.Item> = emptyList(),
    isComplete: Boolean = false,
    isOnboarding: Boolean = false,
    onNavigateBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        if (isComplete) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(CommonR.string.setup_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Button(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(stringResource(CommonR.string.general_continue))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp),
            ) {
                items(items, key = { it.stableId }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        // Minimal rendering — detailed card composables will be added later
                        Text(
                            text = item.state.type.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                        if (!item.state.isComplete) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
