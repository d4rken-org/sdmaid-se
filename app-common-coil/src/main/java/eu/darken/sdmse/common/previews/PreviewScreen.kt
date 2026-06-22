package eu.darken.sdmse.common.previews

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.NavigateBefore
import androidx.compose.material.icons.automirrored.twotone.NavigateNext
import androidx.compose.material.icons.twotone.BrokenImage
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.load
import com.github.panpf.zoomimage.CoilZoomImageView
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun PreviewScreenHost(
    route: PreviewRoute,
    vm: PreviewViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    PreviewScreen(
        paths = route.options.paths,
        initialPosition = route.options.position,
        resolveLookup = vm::resolveLookup,
        onNavigateUp = vm::navUp,
    )
}

@Composable
internal fun PreviewScreen(
    paths: List<APath> = emptyList(),
    initialPosition: Int = 0,
    resolveLookup: suspend (APath) -> APathLookup<*>? = { null },
    onNavigateUp: () -> Unit = {},
) {
    if (paths.isEmpty()) {
        LaunchedEffect(Unit) { onNavigateUp() }
        return
    }

    Dialog(
        onDismissRequest = onNavigateUp,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialPosition.coerceIn(0, paths.lastIndex),
            pageCount = { paths.size },
        )

        // Track zoom of the CURRENT page only (mirrors the legacy ZoomAwareViewPager, which checked
        // the current item synchronously). A per-page Set would go stale when an off-screen zoomed
        // page is disposed without reporting zoom=false, permanently locking pager swipe.
        var currentPageZoomed by remember { mutableStateOf(false) }
        LaunchedEffect(pagerState.currentPage) { currentPageZoomed = false }
        val coroutineScope = rememberCoroutineScope()

        ImmersiveSystemBars()

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !currentPageZoomed,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    PreviewPage(
                        path = paths[page],
                        resolveLookup = resolveLookup,
                        onZoomChanged = { isZoomed ->
                            // Only the visible page drives swipe-lock; ignore reports from pages
                            // entering/leaving composition during an animated page change.
                            if (page == pagerState.currentPage) currentPageZoomed = isZoomed
                        },
                    )
                }

                PreviewHeader(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    counter = "${pagerState.currentPage + 1} / ${paths.size}",
                    onClose = onNavigateUp,
                )

                if (paths.size > 1) {
                    FilledTonalIconButton(
                        onClick = {
                            coroutineScope.launch {
                                val target = (pagerState.currentPage - 1 + paths.size) % paths.size
                                pagerState.animateScrollToPage(target)
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(16.dp),
                    ) {
                        Icon(Icons.AutoMirrored.TwoTone.NavigateBefore, contentDescription = null)
                    }
                    FilledTonalIconButton(
                        onClick = {
                            coroutineScope.launch {
                                val target = (pagerState.currentPage + 1) % paths.size
                                pagerState.animateScrollToPage(target)
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(16.dp),
                    ) {
                        Icon(Icons.AutoMirrored.TwoTone.NavigateNext, contentDescription = null)
                    }
                }
            }
        }
    }
}

/**
 * Hides the status + navigation bars for the duration of this full-screen preview Dialog and
 * restores them on dispose (legacy PreviewFragment parity — the immersive photo-viewer experience).
 * Bars reappear transiently on edge-swipe.
 */
@Composable
private fun ImmersiveSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun PreviewHeader(
    modifier: Modifier = Modifier,
    counter: String,
    onClose: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.add(WindowInsets.displayCutout))
                .padding(horizontal = 8.dp),
        ) {
            FilledTonalIconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.TwoTone.Close,
                    contentDescription = stringResource(CommonR.string.general_close_action),
                )
            }
            Text(
                text = counter,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 48.dp),
            )
        }
    }
}

// The full PreviewScreen renders inside a Dialog with an AndroidView-hosted CoilZoomImageView and
// an ImmersiveSystemBars effect that casts view.parent to DialogWindowProvider — none of which
// render in the @Preview surface. We preview the PreviewHeader chrome instead.
@Preview2
@Composable
private fun PreviewHeaderPreview() {
    PreviewWrapper {
        PreviewHeader(
            modifier = Modifier.fillMaxWidth(),
            counter = "2 / 5",
            onClose = {},
        )
    }
}

@Composable
private fun PreviewPage(
    path: APath,
    resolveLookup: suspend (APath) -> APathLookup<*>?,
    onZoomChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var lookup by remember(path) { mutableStateOf<APathLookup<*>?>(null) }
    var loadFailed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        val resolved = resolveLookup(path)
        if (resolved == null) loadFailed = true else lookup = resolved
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val resolvedLookup = lookup
        if (resolvedLookup == null) {
            // resolveLookup() returning null means it failed (the error is already surfaced via
            // ErrorEventHandler) — show a broken-image marker instead of spinning forever.
            if (loadFailed) {
                Icon(
                    imageVector = Icons.TwoTone.BrokenImage,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            var imageView by remember { mutableStateOf<CoilZoomImageView?>(null) }

            AndroidView(
                factory = { ctx ->
                    CoilZoomImageView(ctx).also { view ->
                        imageView = view
                    }
                },
                update = { view ->
                    view.load(resolvedLookup)
                },
                modifier = Modifier.fillMaxSize(),
            )

            LaunchedEffect(imageView) {
                val view = imageView ?: return@LaunchedEffect
                combine(
                    view.zoomable.transformState,
                    view.zoomable.minScaleState,
                ) { transform, minScale ->
                    transform.scaleX > minScale * 1.05f
                }
                    .distinctUntilChanged()
                    .collect { isZoomed -> onZoomChanged(isZoomed) }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.systemBars.add(WindowInsets.displayCutout))
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = resolvedLookup.path,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = Formatter.formatFileSize(context, resolvedLookup.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
