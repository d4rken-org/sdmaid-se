package eu.darken.sdmse.swiper.ui.swipe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.exclusion.R as ExclusionR
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.common.previews.PreviewRoute
import eu.darken.sdmse.swiper.R
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.ui.SwiperSwipeRoute
import eu.darken.sdmse.swiper.ui.swipe.items.SwiperActionBar
import eu.darken.sdmse.swiper.ui.swipe.items.SwiperGestureOverlay
import eu.darken.sdmse.swiper.ui.swipe.items.SwiperProgressPager
import eu.darken.sdmse.swiper.ui.swipe.items.SwiperStatsCard
import eu.darken.sdmse.swiper.ui.swipe.items.SwiperSwipeBackCard
import eu.darken.sdmse.swiper.ui.swipe.items.SwiperSwipeCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SwiperSwipeScreenHost(
    route: SwiperSwipeRoute,
    vm: SwiperSwipeViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(route) { vm.bindRoute(route) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val openNotSupportedText = stringResource(CommonR.string.general_error_no_compatible_app_found_msg)

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SwiperSwipeViewModel.Event.TriggerHapticFeedback ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                is SwiperSwipeViewModel.Event.OpenExternally -> runCatching {
                    context.startActivity(event.intent)
                }.onFailure { snackbarHostState.showSnackbar(openNotSupportedText) }
                is SwiperSwipeViewModel.Event.ShowOpenNotSupported ->
                    snackbarHostState.showSnackbar(openNotSupportedText)
            }
        }
    }

    SwiperSwipeScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onNavigateToStatus = vm::navigateToStatus,
        onSetDecision = vm::setDecision,
        onSkip = vm::skip,
        onUndo = vm::undo,
        onSetCurrentIndex = vm::setCurrentIndex,
        onOpenExternally = vm::openExternally,
        onExcludeAndRemove = vm::excludeAndRemove,
        onDismissGestureOverlay = vm::dismissGestureOverlay,
        onOpenPreview = { item ->
            vm.navTo(PreviewRoute(PreviewOptions(paths = listOf(item.lookup.lookedUp), position = 0)))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwiperSwipeScreen(
    stateSource: StateFlow<SwiperSwipeViewModel.State?> = MutableStateFlow(null),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onNavigateToStatus: () -> Unit = {},
    onSetDecision: (Long, SwipeDecision) -> Unit = { _, _ -> },
    onSkip: () -> Unit = {},
    onUndo: () -> Unit = {},
    onSetCurrentIndex: (Int) -> Unit = {},
    onOpenExternally: (SwipeItem) -> Unit = {},
    onExcludeAndRemove: (SwipeItem) -> Unit = {},
    onDismissGestureOverlay: () -> Unit = {},
    onOpenPreview: (SwipeItem) -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()

    var showHelpDialog by remember { mutableStateOf(false) }
    var excludeRequest by remember { mutableStateOf<SwipeItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state?.sessionLabel ?: stringResource(R.string.swiper_label))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(CommonR.string.general_close_action),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(CommonR.string.general_help_action),
                        )
                    }
                    IconButton(onClick = onNavigateToStatus) {
                        BadgedBox(
                            badge = {
                                val undecided = state?.undecidedCount ?: 0
                                if (undecided > 0) {
                                    Badge { Text(text = undecided.toString()) }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ListAlt,
                                contentDescription = stringResource(R.string.swiper_review_action),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            val current = state
            if (current != null) {
                SwiperActionBar(
                    canUndo = current.canUndo,
                    swapDirections = current.swapDirections,
                    hasCurrentItem = current.currentItem != null,
                    onDelete = {
                        current.currentItem?.let { onSetDecision(it.id, SwipeDecision.DELETE) }
                    },
                    onKeep = {
                        current.currentItem?.let { onSetDecision(it.id, SwipeDecision.KEEP) }
                    },
                    onUndo = onUndo,
                    onSkip = onSkip,
                    onSkipLongPress = {
                        excludeRequest = current.currentItem
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        val current = state
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            SwiperStatsCard(state = current)
            SwiperProgressPager(
                items = current.items,
                currentIndex = current.currentIndex,
                onItemClick = onSetCurrentIndex,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val nextItem = current.nextItem?.takeIf { it.id != current.currentItem?.id }
                if (nextItem != null) {
                    SwiperSwipeBackCard(
                        item = nextItem,
                        showDetails = current.showDetails,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                val front = current.currentItem
                if (front != null) {
                    SwiperSwipeCard(
                        item = front,
                        canUndo = current.canUndo,
                        swapDirections = current.swapDirections,
                        showDetails = current.showDetails,
                        sessionPosition = current.currentItemOriginalIndex?.plus(1) ?: (current.currentIndex + 1),
                        totalItems = current.totalItems,
                        onSwipeKeep = { onSetDecision(front.id, SwipeDecision.KEEP) },
                        onSwipeDelete = { onSetDecision(front.id, SwipeDecision.DELETE) },
                        onSwipeSkip = onSkip,
                        onSwipeUndo = onUndo,
                        onPreviewClick = { onOpenPreview(front) },
                        onOpenExternallyClick = { onOpenExternally(front) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (current.showGestureOverlay && current.currentItem != null) {
            SwiperGestureOverlay(
                swapDirections = current.swapDirections,
                onDismiss = onDismissGestureOverlay,
            )
        }
    }

    if (showHelpDialog) {
        val swap = state?.swapDirections == true
        val leftAction =
            if (swap) stringResource(R.string.swiper_keep_action)
            else stringResource(CommonR.string.general_delete_action)
        val rightAction =
            if (swap) stringResource(CommonR.string.general_delete_action)
            else stringResource(R.string.swiper_keep_action)
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(stringResource(R.string.swiper_help_title)) },
            text = { Text(stringResource(R.string.swiper_help_message, leftAction, rightAction)) },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    excludeRequest?.let { item ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { excludeRequest = null },
            title = { Text(stringResource(ExclusionR.string.exclusion_create_action)) },
            text = {
                Text(
                    stringResource(
                        R.string.swiper_exclude_confirmation_message,
                        item.lookup.userReadablePath.get(context),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onExcludeAndRemove(item)
                    excludeRequest = null
                }) {
                    Text(stringResource(CommonR.string.general_exclude_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { excludeRequest = null }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }
}
