package eu.darken.sdmse.common.debug.logviewer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun LogViewScreenHost(
    vm: LogViewViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LogViewScreen(
        logSource = vm.log,
        onNavigateUp = vm::navUp,
    )
}

@Composable
internal fun LogViewScreen(
    logSource: Flow<List<String>> = flowOf(emptyList()),
    onNavigateUp: () -> Unit = {},
) {
    val logLines = logSource.collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new lines arrive. Key on the last line, not size: the buffer is
    // capped at 50, so size stops changing and a size-keyed effect would freeze after 50 lines.
    LaunchedEffect(logLines.value.lastOrNull()) {
        if (logLines.value.isNotEmpty()) {
            listState.animateScrollToItem(logLines.value.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_logview_screen_title)) },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(CommonR.string.general_navigate_up_action),
                        onClick = onNavigateUp,
                    )
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp),
            state = listState,
        ) {
            items(logLines.value) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    ),
                )
            }
        }
    }
}

@Preview2
@Composable
private fun LogViewScreenPreview() {
    PreviewWrapper {
        LogViewScreen(
            logSource = flowOf(
                listOf(
                    "D | Example log line",
                    "W | Another line",
                    "E | Error line",
                )
            ),
            onNavigateUp = {},
        )
    }
}
