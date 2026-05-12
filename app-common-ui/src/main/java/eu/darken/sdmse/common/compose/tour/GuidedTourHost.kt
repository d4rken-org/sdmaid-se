package eu.darken.sdmse.common.compose.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal const val MISSING_TARGET_GRACE_MS = 600L

/**
 * How the current step should lay itself out.
 *
 * [Anchored] is the normal case: a target rect is known, cutout + tail toward it. [Centerless]
 * is used when the step has `targetId = null` (intro/outro copy) — uniform dim, centered bubble,
 * no tail. [Pending] means the step has a target id but the registry hasn't seen its rect yet;
 * the host waits `MISSING_TARGET_GRACE_MS` and auto-skips if it still hasn't arrived.
 */
internal sealed interface StepLayout {
    data class Anchored(val rect: Rect) : StepLayout
    data object Centerless : StepLayout
    data object Pending : StepLayout
}

private fun resolveStepLayout(step: TourStep, registry: TourTargetRegistry): StepLayout {
    val tid = step.targetId ?: return StepLayout.Centerless
    return registry.get(tid)?.let { StepLayout.Anchored(it) } ?: StepLayout.Pending
}

@Composable
fun GuidedTourHost(
    session: StateFlow<TourSession?>,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkipForNow: () -> Unit,
    onDontShowAgain: () -> Unit,
    modifier: Modifier = Modifier,
    registry: TourTargetRegistry = remember { TourTargetRegistry() },
    content: @Composable () -> Unit,
) {
    val current by session.collectAsState()
    val step = current?.currentStep
    // Bounds are stored in root coords (see Modifier.guidedTourTarget) — only valid while the
    // host is mounted at the Compose root.
    val layout = step?.let { resolveStepLayout(it, registry) }

    LaunchedEffect(current, step?.targetId, layout) {
        if (current == null || step == null) return@LaunchedEffect
        if (layout !is StepLayout.Pending) return@LaunchedEffect
        delay(MISSING_TARGET_GRACE_MS)
        val tid = step.targetId ?: return@LaunchedEffect
        if (registry.get(tid) == null) onNext()
    }

    CompositionLocalProvider(LocalTourTargetRegistry provides registry) {
        Box(modifier) {
            content()

            val activeSession = current
            val renderable = layout as? StepLayout.Anchored ?: (layout as? StepLayout.Centerless)
            if (activeSession != null && step != null && renderable != null) {
                // BackHandler is composed AFTER content() so it registers later and wins LIFO
                // dispatch over any back handlers in the wrapped screens. Back press = soft skip;
                // the in-bubble confirm only triggers from explicit X / Skip controls.
                BackHandler(enabled = true, onBack = onSkipForNow)
                TourOverlay(
                    layout = renderable,
                    clickProtection = activeSession.definition.clickProtection,
                    step = step,
                    session = activeSession,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSkipForNow = onSkipForNow,
                    onDontShowAgain = onDontShowAgain,
                )
            }
        }
    }
}

@Composable
private fun TourOverlay(
    layout: StepLayout,
    clickProtection: Boolean,
    step: TourStep,
    session: TourSession,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkipForNow: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    val density = LocalDensity.current
    val padding = with(density) { 8.dp.toPx() }
    val cornerRadius = with(density) { 16.dp.toPx() }
    val strokeWidth = with(density) { 2.dp.toPx() }
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val anchored = layout as? StepLayout.Anchored

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // Offscreen compositing is only needed for the BlendMode.Clear cutout; centerless
            // mode draws a single opaque rect, so we can skip it and save an allocated layer.
            .then(
                if (anchored != null) {
                    Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                } else Modifier,
            )
            .then(
                if (clickProtection) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                } else Modifier
            ),
    ) {
        drawRect(Color.Black.copy(alpha = 0.72f))
        if (anchored != null) {
            val rect = anchored.rect
            // Punch a transparent rounded-rect hole around the target.
            val cutoutTopLeft = Offset(rect.left - padding, rect.top - padding)
            val cutoutSize = Size(rect.width + 2 * padding, rect.height + 2 * padding)
            drawRoundRect(
                color = Color.Transparent,
                topLeft = cutoutTopLeft,
                size = cutoutSize,
                cornerRadius = CornerRadius(cornerRadius),
                blendMode = BlendMode.Clear,
            )
            // Accent stroke around the cutout — gives positive selection signal instead of just
            // "less dimmed". Drawn after Clear with default SrcOver blend so the stroke is opaque.
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(cutoutTopLeft.x - strokeWidth / 2f, cutoutTopLeft.y - strokeWidth / 2f),
                size = Size(cutoutSize.width + strokeWidth, cutoutSize.height + strokeWidth),
                cornerRadius = CornerRadius(cornerRadius + strokeWidth / 2f),
                style = Stroke(width = strokeWidth),
            )
        }
    }

    TourBubble(
        step = step,
        layout = layout,
        session = session,
        onNext = onNext,
        onPrevious = onPrevious,
        onSkipForNow = onSkipForNow,
        onDontShowAgain = onDontShowAgain,
    )
}

// region Previews

private val previewHostStep = TourStep(
    stepId = "preview",
    title = "Setup".toCaString(),
    body = (
        "Setup determines what SD Maid can do, based on permissions, " +
            "device behavior, and Android restrictions."
        ).toCaString(),
)

private val previewHostSession = TourSession(
    definition = TourDefinition(
        id = TourId("preview.host.tour"),
        steps = List(4) { i ->
            TourStep(
                stepId = "step$i",
                title = "Step ${i + 1}".toCaString(),
                body = "Body of step ${i + 1}".toCaString(),
            )
        },
        clickProtection = true,
    ),
    stepIndex = 0,
)

@Preview2
@Composable
private fun GuidedTourHostPreviewActive() {
    PreviewWrapper {
        val targetRect = Rect(left = 60f, top = 240f, right = 660f, bottom = 360f)
        val registry = remember {
            TourTargetRegistry().also { it.put("step0", targetRect, owner = "preview-owner") }
        }
        val sessionFlow = remember { MutableStateFlow<TourSession?>(previewHostSession) }
        Box(modifier = Modifier.fillMaxSize()) {
            GuidedTourHost(
                session = sessionFlow,
                onNext = {},
                onPrevious = {},
                onSkipForNow = {},
                onDontShowAgain = {},
                modifier = Modifier.fillMaxSize(),
                registry = registry,
            ) {
                // Stand-in for the wrapped screen content so the cutout has something visible.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(16.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Text(text = "Highlighted target lives here", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Preview2
@Composable
private fun GuidedTourHostPreviewCenterless() {
    PreviewWrapper {
        val centerlessSession = TourSession(
            definition = TourDefinition(
                id = TourId("preview.host.tour.centerless"),
                steps = listOf(
                    TourStep(
                        stepId = "overview",
                        targetId = null,
                        title = "Dashboard".toCaString(),
                        body = (
                            "This is the dashboard, your home for all of SD Maid's tools. " +
                                "Each card is a tool — open one to use it."
                            ).toCaString(),
                    ),
                    TourStep(stepId = "step1", body = "Body of step 2".toCaString()),
                ),
                clickProtection = true,
            ),
            stepIndex = 0,
        )
        val sessionFlow = remember { MutableStateFlow<TourSession?>(centerlessSession) }
        Box(modifier = Modifier.fillMaxSize()) {
            GuidedTourHost(
                session = sessionFlow,
                onNext = {},
                onPrevious = {},
                onSkipForNow = {},
                onDontShowAgain = {},
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(16.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Text(
                        text = "Behind the dim — no cutout for centerless steps",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun GuidedTourHostPreviewIdle() {
    PreviewWrapper {
        val sessionFlow = remember { MutableStateFlow<TourSession?>(null) }
        Box(modifier = Modifier.fillMaxSize()) {
            GuidedTourHost(
                session = sessionFlow,
                onNext = {},
                onPrevious = {},
                onSkipForNow = {},
                onDontShowAgain = {},
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Idle host — content visible, no overlay",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// endregion
