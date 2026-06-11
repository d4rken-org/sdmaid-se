package eu.darken.sdmse.common.compose.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import eu.darken.sdmse.common.compose.LocalFocusHighlightController
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

    // Confirm-exit UI state, hoisted here so the BackHandler below can drive it. Keyed per tour
    // AND step: a step change while the confirm is showing (e.g. a grace-skip advancing past a
    // vanished target) must reset back to the step view, never leak the confirm into a new step.
    var showExitConfirm by remember(current?.definition?.id?.raw, current?.stepIndex) { mutableStateOf(false) }

    // True while any node inside the tour bubble holds focus. Scopes the key shield below:
    // bubble buttons must keep working, everything else must not react to the D-pad.
    var bubbleHasFocus by remember { mutableStateOf(false) }
    val keyShieldActive = current?.definition?.clickProtection == true

    // While keys are shielded and focus hasn't been pulled into the bubble yet (notably the
    // pending-target grace window), the app-wide focus ring would highlight a background
    // control above the scrim — a control the user is deliberately blocked from. Hide it.
    val focusHighlight = LocalFocusHighlightController.current
    val suppressFocusRing = keyShieldActive && !bubbleHasFocus
    DisposableEffect(focusHighlight, suppressFocusRing) {
        focusHighlight?.suppressed = suppressFocusRing
        onDispose { focusHighlight?.suppressed = false }
    }

    CompositionLocalProvider(LocalTourTargetRegistry provides registry) {
        Box(
            modifier.onPreviewKeyEvent { event ->
                // clickProtection only consumes pointer events; on TV the D-pad would still reach
                // (and activate!) focusable content under the scrim. Swallow navigation/confirm
                // keys at the root (an ancestor of all content) unless focus is in the bubble.
                // Keyed on the session, not the resolved layout, so the Pending grace window is
                // covered too. BACK stays untouched (previous step / exit confirm via BackHandler).
                if (!keyShieldActive || bubbleHasFocus) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                        -> true

                    else -> false
                }
            },
        ) {
            content()

            val activeSession = current
            val renderable = layout as? StepLayout.Anchored ?: (layout as? StepLayout.Centerless)
            if (activeSession != null && step != null) {
                // BackHandler is composed AFTER content() so it registers later and wins LIFO
                // dispatch over any back handlers in the wrapped screens. Back steps backwards
                // through the tour (TV remote muscle memory); at the first step it opens the same
                // exit confirm as the X button. Back never cancels the tour directly. Composed for
                // ANY active session — including the Pending grace window, where back at step 0 is
                // consumed silently (no bubble exists to show the confirm in).
                BackHandler(enabled = true) {
                    when {
                        showExitConfirm -> showExitConfirm = false
                        activeSession.stepIndex > 0 -> onPrevious()
                        renderable != null -> showExitConfirm = true
                        else -> Unit
                    }
                }
            }
            if (activeSession != null && step != null && renderable != null) {
                TourOverlay(
                    layout = renderable,
                    clickProtection = activeSession.definition.clickProtection,
                    step = step,
                    session = activeSession,
                    showConfirm = showExitConfirm,
                    onShowConfirmChange = { showExitConfirm = it },
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSkipForNow = onSkipForNow,
                    onDontShowAgain = onDontShowAgain,
                    onBubbleFocusChanged = { bubbleHasFocus = it },
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
    showConfirm: Boolean,
    onShowConfirmChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkipForNow: () -> Unit,
    onDontShowAgain: () -> Unit,
    onBubbleFocusChanged: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val padding = with(density) { 8.dp.toPx() }
    val cornerRadius = with(density) { 16.dp.toPx() }
    val strokeWidth = with(density) { 3.dp.toPx() }
    val maxGlowWidth = with(density) { 10.dp.toPx() }
    val accentColor = MaterialTheme.colorScheme.primary
    val anchored = layout as? StepLayout.Anchored

    // Breathing glow around the cutout ring. A static ring tested as too subtle at TV viewing
    // distance — motion is what pulls the eye on a 10-foot UI. Only runs while a step is anchored.
    val glowPulse = if (anchored != null) {
        val transition = rememberInfiniteTransition(label = "tour-ring")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "tour-ring-glow",
        )
    } else null

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
            // Breathing outer glow: expands and fades as the pulse progresses, drawing the eye
            // toward the cutout at TV viewing distance.
            val pulse = glowPulse?.value ?: 0f
            if (pulse > 0f) {
                val glowWidth = strokeWidth + maxGlowWidth * pulse
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.45f * (1f - pulse * 0.7f)),
                    topLeft = Offset(cutoutTopLeft.x - glowWidth / 2f, cutoutTopLeft.y - glowWidth / 2f),
                    size = Size(cutoutSize.width + glowWidth, cutoutSize.height + glowWidth),
                    cornerRadius = CornerRadius(cornerRadius + glowWidth / 2f),
                    style = Stroke(width = glowWidth),
                )
            }
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
        showConfirm = showConfirm,
        onShowConfirmChange = onShowConfirmChange,
        onNext = onNext,
        onPrevious = onPrevious,
        onSkipForNow = onSkipForNow,
        onDontShowAgain = onDontShowAgain,
        onFocusWithinChanged = onBubbleFocusChanged,
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
