package eu.darken.sdmse.common.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Placeholder shown when the NavDisplay's entryProvider receives a route that no [NavigationEntry]
 * has registered. During the incremental Compose rewrite this is expected for routes whose
 * Fragment-based screens haven't been converted yet (e.g. CustomFilterList, Picker, ArbiterConfig,
 * Reports). Without this fallback NavDisplay throws `IllegalStateException: Unknown screen …`
 * and kills the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnknownDestinationScreen(
    routeLabel: String,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coming soon") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "This screen is still being migrated to the new UI.",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = routeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Preview2
@Composable
private fun UnknownDestinationScreenPreview() {
    PreviewWrapper {
        UnknownDestinationScreen(
            routeLabel = "CustomFilterListRoute",
            onNavigateUp = {},
        )
    }
}
