package eu.darken.sdmse.common.compose.tour

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.SdmMascot
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.ui.R as UiR

// The tail's vertical extent must stay in sync between the SpeechBubbleShape (which draws the
// triangle outside the rounded-rect body) and the content padding (which reserves matching
// space so text doesn't overlap the tail). Single source of truth.
private val TAIL_HEIGHT = 10.dp

@Composable
internal fun TourBubble(
    step: TourStep,
    targetRect: Rect,
    session: TourSession,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkipForNow: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val maxHPx = with(density) { maxHeight.toPx() }
        val maxWPx = with(density) { maxWidth.toPx() }

        val isNarrow = maxWidth < 360.dp
        val placeBelow = targetRect.center.y < maxHPx / 2f

        val sideMargin = 16.dp
        val startPad = insets.calculateStartPadding(layoutDirection) + sideMargin
        val endPad = insets.calculateEndPadding(layoutDirection) + sideMargin
        val startPadPx = with(density) { startPad.toPx() }
        val endPadPx = with(density) { endPad.toPx() }

        // Cap the bubble's outer width so it doesn't stretch into giant rectangles on landscape
        // phones and tablets. The bubble stays centered horizontally; the visible content area is
        // the cap minus the inset+sidemargin padding on each side.
        val maxBubbleOuter = 480.dp
        val maxBubbleOuterPx = with(density) { maxBubbleOuter.toPx() }
        val bubbleVisibleWidthPx = (
            minOf(maxWPx, maxBubbleOuterPx) - startPadPx - endPadPx
            ).coerceAtLeast(1f)
        val bubbleLeftPx = (maxWPx - bubbleVisibleWidthPx) / 2f
        val tailXBias = ((targetRect.center.x - bubbleLeftPx) / bubbleVisibleWidthPx)
            .coerceIn(0f, 1f)

        val bubbleMaxHeight = (maxHeight
            - insets.calculateTopPadding()
            - insets.calculateBottomPadding()
            - 32.dp).coerceAtLeast(160.dp)

        val rawY = with(density) {
            if (placeBelow) (targetRect.bottom + 16f).toDp()
            else (maxHPx - targetRect.top + 16f).toDp()
        }
        val clampedY = if (placeBelow) {
            rawY.coerceAtLeast(insets.calculateTopPadding())
        } else {
            rawY.coerceAtLeast(insets.calculateBottomPadding())
        }

        Box(
            modifier = Modifier
                .align(if (placeBelow) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(
                    top = if (placeBelow) clampedY else 0.dp,
                    bottom = if (!placeBelow) clampedY else 0.dp,
                    start = startPad,
                    end = endPad,
                )
                .widthIn(max = maxBubbleOuter)
                .heightIn(max = bubbleMaxHeight),
        ) {
            BubbleCard(
                step = step,
                session = session,
                placeBelow = placeBelow,
                tailXBias = tailXBias,
                isNarrow = isNarrow,
                onNext = onNext,
                onPrevious = onPrevious,
                onSkipForNow = onSkipForNow,
                onDontShowAgain = onDontShowAgain,
            )
        }
    }
}

@Composable
private fun BubbleCard(
    step: TourStep,
    session: TourSession,
    placeBelow: Boolean,
    tailXBias: Float,
    isNarrow: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkipForNow: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    var showConfirm by rememberSaveable(session.definition.id.raw) { mutableStateOf(false) }

    val tintedBubble = MaterialTheme.colorScheme.primary
        .copy(alpha = 0.06f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val tailEdge = if (placeBelow) SpeechBubbleShape.Edge.TOP else SpeechBubbleShape.Edge.BOTTOM
    val shape = SpeechBubbleShape(
        cornerRadius = 20.dp,
        tail = SpeechBubbleShape.TailSpec(
            edge = tailEdge,
            xBias = tailXBias,
            width = 16.dp,
            height = TAIL_HEIGHT,
        ),
    )

    Surface(
        shape = shape,
        color = tintedBubble,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (placeBelow) TAIL_HEIGHT else 0.dp,
                    bottom = if (!placeBelow) TAIL_HEIGHT else 0.dp,
                ),
        ) {
            AnimatedContent(
                targetState = showConfirm,
                transitionSpec = {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                },
                label = "tour-bubble-content",
            ) { confirming ->
                if (confirming) {
                    ConfirmContent(
                        onContinue = { showConfirm = false },
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
                        onRequestExit = { showConfirm = true },
                    )
                }
            }
        }
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

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = mascotWidth + 4.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        ) {
            // Header: [◀] | dots | [▶ / ✓] — icon-only buttons so we don't have to translate
            // navigation glyphs. Strings are still attached as content descriptions for a11y.
            // Previous is hidden on the very first step (no earlier step to go back to).
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (session.stepIndex > 0) {
                    Button(
                        onClick = onPrevious,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = stringResource(UiR.string.tour_action_previous),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                StepDots(
                    current = session.stepIndex,
                    total = session.definition.steps.size,
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onNext,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
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
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
        // Drawn last in the Box so it sits above the mascot in z-order.
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
            modifier = Modifier.fillMaxWidth(),
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
private fun StepDots(current: Int, total: Int) {
    val activeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Row(
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
                placeBelow = true,
                tailXBias = 0.5f,
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
                placeBelow = false,
                tailXBias = 0.3f,
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
