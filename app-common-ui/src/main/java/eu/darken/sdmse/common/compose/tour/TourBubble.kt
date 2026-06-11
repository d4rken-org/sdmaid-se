package eu.darken.sdmse.common.compose.tour

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ArrowForward
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.SdmMascot
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.ui.R as UiR

// The tail's vertical extent must stay in sync between SpeechBubbleShape (which draws the tail
// triangle outside the rounded-rect body) and SpeechBubbleSurface's content padding (which
// reserves matching space so content doesn't overlap the tail). Single source of truth.
private val TailHeight = 10.dp
private val MaxBubbleWidth = 480.dp
private val SideMargin = 16.dp
private val TargetGap = 16.dp
private val NarrowThreshold = 360.dp

@Composable
internal fun TourBubble(
    step: TourStep,
    layout: StepLayout,
    session: TourSession,
    showConfirm: Boolean,
    onShowConfirmChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkipForNow: () -> Unit,
    onDontShowAgain: () -> Unit,
    onFocusWithinChanged: (Boolean) -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current

        val isNarrow = maxWidth < NarrowThreshold
        val startPad = insets.calculateStartPadding(layoutDirection) + SideMargin
        val endPad = insets.calculateEndPadding(layoutDirection) + SideMargin
        val topPad = insets.calculateTopPadding() + SideMargin
        val bottomPad = insets.calculateBottomPadding() + SideMargin

        when (layout) {
            is StepLayout.Anchored -> AnchoredBubble(
                rect = layout.rect,
                step = step,
                session = session,
                showConfirm = showConfirm,
                onShowConfirmChange = onShowConfirmChange,
                isNarrow = isNarrow,
                density = density,
                insets = insets,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                startPad = startPad,
                endPad = endPad,
                onNext = onNext,
                onPrevious = onPrevious,
                onSkipForNow = onSkipForNow,
                onDontShowAgain = onDontShowAgain,
                onFocusWithinChanged = onFocusWithinChanged,
            )

            StepLayout.Centerless -> CenterlessBubble(
                step = step,
                session = session,
                showConfirm = showConfirm,
                onShowConfirmChange = onShowConfirmChange,
                isNarrow = isNarrow,
                maxHeight = maxHeight,
                startPad = startPad,
                endPad = endPad,
                topPad = topPad,
                bottomPad = bottomPad,
                onNext = onNext,
                onPrevious = onPrevious,
                onSkipForNow = onSkipForNow,
                onDontShowAgain = onDontShowAgain,
                onFocusWithinChanged = onFocusWithinChanged,
            )

            // Pending is filtered out by GuidedTourHost before this point.
            StepLayout.Pending -> Unit
        }
    }
}

@Composable
private fun BoxScope.AnchoredBubble(
    rect: Rect,
    step: TourStep,
    session: TourSession,
    showConfirm: Boolean,
    onShowConfirmChange: (Boolean) -> Unit,
    isNarrow: Boolean,
    density: Density,
    insets: PaddingValues,
    maxWidth: Dp,
    maxHeight: Dp,
    startPad: Dp,
    endPad: Dp,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkipForNow: () -> Unit,
    onDontShowAgain: () -> Unit,
    onFocusWithinChanged: (Boolean) -> Unit,
) {
    val maxHPx = with(density) { maxHeight.toPx() }
    val maxWPx = with(density) { maxWidth.toPx() }
    val topInsetPx = with(density) { insets.calculateTopPadding().toPx() }
    val bottomInsetPx = with(density) { insets.calculateBottomPadding().toPx() }
    val gapPx = with(density) { TargetGap.toPx() }

    // Pick above/below by usable space, not by target center. Keeps long bubbles from
    // overflowing on tall targets that happen to sit just above the screen midpoint.
    val availableBelowPx = maxHPx - rect.bottom - bottomInsetPx - gapPx
    val availableAbovePx = rect.top - topInsetPx - gapPx
    val placeBelow = availableBelowPx >= availableAbovePx

    val startPadPx = with(density) { startPad.toPx() }
    val endPadPx = with(density) { endPad.toPx() }
    val maxBubbleWidthPx = with(density) { MaxBubbleWidth.toPx() }

    // The Surface fills available width minus side padding, capped at MaxBubbleWidth.
    // Compute the actual body width and its left edge, so the tail can target a real x.
    val availableWidthPx = (maxWPx - startPadPx - endPadPx).coerceAtLeast(1f)
    val bubbleBodyWidthPx = availableWidthPx.coerceAtMost(maxBubbleWidthPx)
    val bubbleBodyLeftPx = startPadPx + (availableWidthPx - bubbleBodyWidthPx) / 2f
    val tailXBias = ((rect.center.x - bubbleBodyLeftPx) / bubbleBodyWidthPx)
        .coerceIn(0f, 1f)

    val rawY = with(density) {
        if (placeBelow) (rect.bottom + gapPx).toDp()
        else (maxHPx - rect.top + gapPx).toDp()
    }
    val clampedY = if (placeBelow) {
        rawY.coerceAtLeast(insets.calculateTopPadding())
    } else {
        rawY.coerceAtLeast(insets.calculateBottomPadding())
    }
    // Far-side inset: keeps the bubble's *other* edge inside the safe area too.
    val farTopPad = insets.calculateTopPadding() + SideMargin
    val farBottomPad = insets.calculateBottomPadding() + SideMargin

    // The bubble's max height is whatever is left between the cutout-side gap and the
    // far-side safe inset, so bottom edges never run under the nav bar.
    val bubbleMaxHeight = if (placeBelow) {
        (maxHeight - clampedY - farBottomPad).coerceAtLeast(160.dp)
    } else {
        (maxHeight - clampedY - farTopPad).coerceAtLeast(160.dp)
    }

    Box(
        modifier = Modifier
            .align(if (placeBelow) Alignment.TopCenter else Alignment.BottomCenter)
            .padding(
                top = if (placeBelow) clampedY else farTopPad,
                bottom = if (!placeBelow) clampedY else farBottomPad,
                start = startPad,
                end = endPad,
            )
            .widthIn(max = MaxBubbleWidth)
            .heightIn(max = bubbleMaxHeight),
    ) {
        BubbleCard(
            step = step,
            session = session,
            tail = BubbleTail.OnEdge(
                edge = if (placeBelow) SpeechBubbleShape.Edge.TOP else SpeechBubbleShape.Edge.BOTTOM,
                xBias = tailXBias,
            ),
            isNarrow = isNarrow,
            showConfirm = showConfirm,
            onShowConfirmChange = onShowConfirmChange,
            onNext = onNext,
            onPrevious = onPrevious,
            onSkipForNow = onSkipForNow,
            onDontShowAgain = onDontShowAgain,
            onFocusWithinChanged = onFocusWithinChanged,
        )
    }
}

@Composable
private fun BoxScope.CenterlessBubble(
    step: TourStep,
    session: TourSession,
    showConfirm: Boolean,
    onShowConfirmChange: (Boolean) -> Unit,
    isNarrow: Boolean,
    maxHeight: Dp,
    startPad: Dp,
    endPad: Dp,
    topPad: Dp,
    bottomPad: Dp,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkipForNow: () -> Unit,
    onDontShowAgain: () -> Unit,
    onFocusWithinChanged: (Boolean) -> Unit,
) {
    // Cap height at the safe area; the body has its own vertical scroll for content that doesn't
    // fit. Match the anchored 160.dp floor — multi-window / split-screen can leave available
    // height below SideMargin * 2 and the bubble would otherwise collapse to an unusable sliver.
    val bubbleMaxHeight = (maxHeight - topPad - bottomPad).coerceAtLeast(160.dp)
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(start = startPad, end = endPad, top = topPad, bottom = bottomPad)
            .widthIn(max = MaxBubbleWidth)
            .heightIn(max = bubbleMaxHeight),
    ) {
        BubbleCard(
            step = step,
            session = session,
            tail = BubbleTail.None,
            isNarrow = isNarrow,
            showConfirm = showConfirm,
            onShowConfirmChange = onShowConfirmChange,
            onNext = onNext,
            onPrevious = onPrevious,
            onSkipForNow = onSkipForNow,
            onDontShowAgain = onDontShowAgain,
            onFocusWithinChanged = onFocusWithinChanged,
        )
    }
}

internal sealed interface BubbleTail {
    data object None : BubbleTail
    data class OnEdge(val edge: SpeechBubbleShape.Edge, val xBias: Float) : BubbleTail
}

@Composable
private fun BubbleCard(
    step: TourStep,
    session: TourSession,
    tail: BubbleTail,
    isNarrow: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkipForNow: () -> Unit,
    onDontShowAgain: () -> Unit,
    onFocusWithinChanged: (Boolean) -> Unit = {},
    // Confirm state is hoisted to GuidedTourHost so its BackHandler can drive the same exit
    // confirm: back at the first step opens it, back while it's showing dismisses it.
    showConfirm: Boolean = false,
    onShowConfirmChange: (Boolean) -> Unit = {},
) {
    SpeechBubbleSurface(
        tail = tail,
        // Focus trap for D-pad/keyboard: once focus is inside the bubble it cycles among the
        // bubble's own controls and cannot wander into the scrimmed background. Entry happens
        // via the explicit focus requests in StepContent/ConfirmContent.
        modifier = Modifier
            .onFocusChanged { onFocusWithinChanged(it.hasFocus) }
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
    ) {
        AnimatedContent(
            targetState = showConfirm,
            transitionSpec = {
                fadeIn(tween(160)) togetherWith fadeOut(tween(120))
            },
            contentAlignment = Alignment.TopStart,
            label = "tour-bubble-content",
        ) { confirming ->
            if (confirming) {
                ConfirmContent(
                    onContinue = { onShowConfirmChange(false) },
                    onSkipForNow = onSkipForNow,
                    onDontShowAgain = onDontShowAgain,
                )
            } else {
                StepContent(
                    step = step,
                    session = session,
                    isNarrow = isNarrow,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onRequestExit = { onShowConfirmChange(true) },
                )
            }
        }
    }
}

/**
 * Speech-bubble container: rounded surface with brand tint + border, an optional tail pointing
 * toward the cutout, and the inset padding that reserves space for the tail. Owns the
 * shape↔padding contract so callers don't have to. With [BubbleTail.None], no tail is drawn and
 * no tail-side padding is reserved (used for centerless intro/outro steps).
 */
@Composable
private fun SpeechBubbleSurface(
    tail: BubbleTail,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tintedSurface = MaterialTheme.colorScheme.primary
        .copy(alpha = 0.06f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val tailSpec = (tail as? BubbleTail.OnEdge)?.let {
        SpeechBubbleShape.TailSpec(
            edge = it.edge,
            xBias = it.xBias,
            width = 16.dp,
            height = TailHeight,
        )
    }
    val shape = SpeechBubbleShape(
        cornerRadius = 20.dp,
        tail = tailSpec,
    )

    Surface(
        modifier = modifier,
        shape = shape,
        color = tintedSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (tailSpec?.edge == SpeechBubbleShape.Edge.TOP) TailHeight else 0.dp,
                    bottom = if (tailSpec?.edge == SpeechBubbleShape.Edge.BOTTOM) TailHeight else 0.dp,
                ),
            content = content,
        )
    }
}

@Composable
private fun StepContent(
    step: TourStep,
    session: TourSession,
    isNarrow: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRequestExit: () -> Unit,
) {
    val context = LocalContext.current
    val mascotWidth = if (isNarrow) 80.dp else 96.dp

    // Pull D-pad/keyboard focus into the bubble whenever the step view (re)appears — this is
    // what arms the focus trap on the surrounding focusGroup. Without it, TV focus stays in
    // the scrimmed background and the tour cannot be advanced at all.
    // Also keyed on the input mode: if the tour starts while in touch mode (screen opened via
    // tap), clickables aren't focusable and the initial request fails silently — the first
    // remote key press flips the mode to Keyboard and this re-runs to claim focus properly.
    val inputModeManager = LocalInputModeManager.current
    val nextFocus = remember { FocusRequester() }
    LaunchedEffect(step.stepId, inputModeManager.inputMode) {
        runCatching { nextFocus.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = mascotWidth + 4.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        ) {
            // Header: three-cell layout via Box(fillMaxWidth) + alignment, so the dots are
            // truly centered regardless of which navigation buttons are visible.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                if (session.stepIndex > 0) {
                    Button(
                        onClick = onPrevious,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = stringResource(UiR.string.tour_action_previous),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (session.definition.steps.size > 1) {
                    StepDots(
                        current = session.stepIndex,
                        total = session.definition.steps.size,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Button(
                    onClick = onNext,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .focusRequester(nextFocus),
                ) {
                    Icon(
                        imageVector = if (session.isLast) Icons.TwoTone.Check
                        else Icons.AutoMirrored.TwoTone.ArrowForward,
                        contentDescription = stringResource(
                            if (session.isLast) UiR.string.tour_action_done
                            else UiR.string.tour_action_next,
                        ),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Focusable so long step text can be scrolled with the D-pad from inside the focus
            // trap (scrollables handle arrow keys when focused). Tinted while focused since
            // plain focusable() has no indication of its own.
            var bodyFocused by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .onFocusChanged { bodyFocused = it.isFocused }
                    .background(
                        color = if (bodyFocused) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        } else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .verticalScroll(rememberScrollState())
                    .focusable(),
            ) {
                step.title?.let { title ->
                    Text(
                        text = title.get(context),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = step.body.get(context),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        SdmMascot(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(top = 24.dp)
                .width(mascotWidth),
        )
        // Close (X) overlay parked on the empty top of the mascot's animation container.
        // Drawn last in the Box so it sits above the mascot in z-order and wins hit-testing.
        IconButton(
            onClick = onRequestExit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 20.dp)
                .size(36.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.Close,
                contentDescription = stringResource(UiR.string.tour_action_cancel),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfirmContent(
    onContinue: () -> Unit,
    onSkipForNow: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    // Mirror of StepContent's focus pull: when the confirm view swaps in via AnimatedContent,
    // re-anchor D-pad focus on the safe default so the trap keeps holding. Keyed on input mode
    // for the same touch-mode-start reason as StepContent.
    val inputModeManager = LocalInputModeManager.current
    val continueFocus = remember { FocusRequester() }
    LaunchedEffect(inputModeManager.inputMode) {
        runCatching { continueFocus.requestFocus() }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(UiR.string.tour_confirm_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(UiR.string.tour_confirm_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(continueFocus),
        ) {
            Text(text = stringResource(UiR.string.tour_confirm_continue))
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onSkipForNow) {
                Text(text = stringResource(UiR.string.tour_confirm_skip_for_now))
            }
            TextButton(
                onClick = onDontShowAgain,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(text = stringResource(UiR.string.tour_confirm_dont_show_again))
            }
        }
    }
}

@Composable
private fun StepDots(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            val isActive = i == current
            Box(
                modifier = Modifier
                    .size(if (isActive) 8.dp else 6.dp)
                    .background(
                        color = if (isActive) activeColor else idleColor,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

// region Previews

private val previewStep = TourStep(
    stepId = "preview",
    title = "Setup".toCaString(),
    body = (
        "Setup determines what SD Maid can do, based on permissions, " +
            "device behavior, and Android restrictions."
        ).toCaString(),
)

private val previewSession = TourSession(
    definition = TourDefinition(
        id = TourId("preview.tour"),
        steps = List(4) { i ->
            TourStep(
                stepId = "step$i",
                title = "Step ${i + 1}".toCaString(),
                body = "Body of step ${i + 1}".toCaString(),
            )
        },
    ),
    stepIndex = 0,
)

@Preview2
@Composable
private fun StepDotsPreviewFirst() {
    PreviewWrapper {
        Box(modifier = Modifier.padding(16.dp)) {
            StepDots(current = 0, total = 4)
        }
    }
}

@Preview2
@Composable
private fun StepDotsPreviewMiddle() {
    PreviewWrapper {
        Box(modifier = Modifier.padding(16.dp)) {
            StepDots(current = 2, total = 4)
        }
    }
}

@Preview2
@Composable
private fun StepContentPreview() {
    PreviewWrapper {
        StepContent(
            step = previewStep,
            session = previewSession,
            isNarrow = false,
            onNext = {},
            onPrevious = {},
            onRequestExit = {},
        )
    }
}

@Preview2
@Composable
private fun StepContentPreviewLastStep() {
    PreviewWrapper {
        StepContent(
            step = previewStep,
            session = previewSession.copy(stepIndex = 3),
            isNarrow = false,
            onNext = {},
            onPrevious = {},
            onRequestExit = {},
        )
    }
}

@Preview2
@Composable
private fun StepContentPreviewSingleStep() {
    PreviewWrapper {
        StepContent(
            step = previewStep,
            session = TourSession(
                definition = TourDefinition(
                    id = TourId("preview.tour.single"),
                    steps = listOf(previewStep),
                ),
                stepIndex = 0,
            ),
            isNarrow = false,
            onNext = {},
            onPrevious = {},
            onRequestExit = {},
        )
    }
}

@Preview2
@Composable
private fun StepContentPreviewNarrow() {
    PreviewWrapper {
        StepContent(
            step = previewStep,
            session = previewSession,
            isNarrow = true,
            onNext = {},
            onPrevious = {},
            onRequestExit = {},
        )
    }
}

@Preview2
@Composable
private fun ConfirmContentPreview() {
    PreviewWrapper {
        ConfirmContent(
            onContinue = {},
            onSkipForNow = {},
            onDontShowAgain = {},
        )
    }
}

@Preview2
@Composable
private fun BubbleCardPreviewTailTop() {
    PreviewWrapper {
        Box(modifier = Modifier.padding(16.dp)) {
            BubbleCard(
                step = previewStep,
                session = previewSession,
                tail = BubbleTail.OnEdge(edge = SpeechBubbleShape.Edge.TOP, xBias = 0.5f),
                isNarrow = false,
                onNext = {},
                onPrevious = {},
                onSkipForNow = {},
                onDontShowAgain = {},
            )
        }
    }
}

@Preview2
@Composable
private fun BubbleCardPreviewTailBottom() {
    PreviewWrapper {
        Box(modifier = Modifier.padding(16.dp)) {
            BubbleCard(
                step = previewStep,
                session = previewSession.copy(stepIndex = 1),
                tail = BubbleTail.OnEdge(edge = SpeechBubbleShape.Edge.BOTTOM, xBias = 0.3f),
                isNarrow = false,
                onNext = {},
                onPrevious = {},
                onSkipForNow = {},
                onDontShowAgain = {},
            )
        }
    }
}

@Preview2
@Composable
private fun BubbleCardPreviewCenterless() {
    PreviewWrapper {
        Box(modifier = Modifier.padding(16.dp)) {
            BubbleCard(
                step = previewStep,
                session = previewSession,
                tail = BubbleTail.None,
                isNarrow = false,
                onNext = {},
                onPrevious = {},
                onSkipForNow = {},
                onDontShowAgain = {},
            )
        }
    }
}

// endregion
