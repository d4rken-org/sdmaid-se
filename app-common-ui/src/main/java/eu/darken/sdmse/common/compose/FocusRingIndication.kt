package eu.darken.sdmse.common.compose

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * App-wide indication that adds a clearly visible focus treatment for non-touch input
 * (D-pad on TV, hardware keyboards) on top of the regular ripple. Touch interaction is
 * unaffected: the ring only renders when focus was gained while the input mode is
 * [InputMode.Keyboard], so programmatic focus requests on touch devices (e.g. onboarding's
 * auto-focused Continue button) don't show it.
 *
 * The highlight is drawn inside the component's own draw scope, so it inherits whatever
 * clip shape the component uses (pill buttons, circular icon buttons, full-width rows):
 * a translucent fill across the bounds plus an inset stroke.
 */
data class FocusRingIndication(
    private val ringColor: Color,
    private val ripple: IndicationNodeFactory,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        CombinedIndicationNode(
            rippleNode = ripple.create(interactionSource),
            ringNode = FocusRingNode(interactionSource, ringColor),
        )
}

/** Carrier that runs the ripple node and the focus ring node side by side. */
private class CombinedIndicationNode(
    rippleNode: DelegatableNode,
    ringNode: FocusRingNode,
) : DelegatingNode() {
    init {
        delegate(rippleNode)
        delegate(ringNode)
    }
}

private class FocusRingNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : DelegatingNode(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private val activeFocus = mutableSetOf<FocusInteraction.Focus>()
    private var showRing = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction -> handle(interaction) }
        }
    }

    private fun handle(interaction: Interaction) {
        when (interaction) {
            is FocusInteraction.Focus -> {
                // Only treat focus gained via key-based navigation as ring-worthy.
                val keysMode = currentValueOf(LocalInputModeManager).inputMode == InputMode.Keyboard
                if (keysMode) activeFocus.add(interaction)
            }

            is FocusInteraction.Unfocus -> activeFocus.remove(interaction.focus)

            else -> return
        }
        val show = activeFocus.isNotEmpty()
        if (show != showRing) {
            showRing = show
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!showRing) return

        // Soft fill so the highlight survives any clip shape, even where the stroke gets cut.
        drawRect(color = color.copy(alpha = FILL_ALPHA))

        val stroke = RING_WIDTH.toPx()
        val inset = stroke / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(RING_CORNER_RADIUS.toPx()),
            style = Stroke(width = stroke),
        )
    }

    companion object {
        private const val FILL_ALPHA = 0.16f
        private val RING_WIDTH = 3.dp
        private val RING_CORNER_RADIUS = 10.dp
    }
}
