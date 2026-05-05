package eu.darken.sdmse.common.compose.tour

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * A rounded rectangle with an optional triangular tail on the top or bottom edge,
 * positioned along the edge by [TailSpec.xBias] (0..1, clamped). The tail is drawn
 * within the shape's bounds, so the caller must reserve [TailSpec.height] of vertical
 * space on the tailed edge (e.g. via padding) so content doesn't overlap the tail.
 */
internal class SpeechBubbleShape(
    private val cornerRadius: Dp,
    private val tail: TailSpec?,
) : Shape {

    enum class Edge { TOP, BOTTOM }

    data class TailSpec(
        val edge: Edge,
        /** 0..1 along the bubble's width, clamped to keep the tail away from corners. */
        val xBias: Float,
        val width: Dp,
        val height: Dp,
    )

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val tailHeightPx = with(density) { tail?.height?.toPx() ?: 0f }
        val tailWidthPx = with(density) { tail?.width?.toPx() ?: 0f }

        val rectTop = if (tail?.edge == Edge.TOP) tailHeightPx else 0f
        val rectBottom = if (tail?.edge == Edge.BOTTOM) size.height - tailHeightPx else size.height

        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(left = 0f, top = rectTop, right = size.width, bottom = rectBottom),
                    cornerRadius = CornerRadius(r),
                ),
            )
            if (tail != null && tailWidthPx > 0f && tailHeightPx > 0f) {
                // On extremely narrow bubbles (width < 2 * cornerRadius + tailWidth) `minCenter`
                // would exceed `maxCenter` and `coerceIn` would throw. Clamp the half-tail to
                // never exceed half the width, then fall back to centering if there is still no
                // valid range.
                val halfTail = (tailWidthPx / 2f).coerceAtMost(size.width / 2f)
                val minCenter = r + halfTail
                val maxCenter = size.width - r - halfTail
                val preferred = tail.xBias.coerceIn(0f, 1f) * size.width
                val xCenter = if (minCenter <= maxCenter) {
                    preferred.coerceIn(minCenter, maxCenter)
                } else {
                    size.width / 2f
                }
                val tailPath = Path().apply {
                    when (tail.edge) {
                        Edge.TOP -> {
                            moveTo(xCenter - halfTail, rectTop)
                            lineTo(xCenter, 0f)
                            lineTo(xCenter + halfTail, rectTop)
                            close()
                        }
                        Edge.BOTTOM -> {
                            moveTo(xCenter - halfTail, rectBottom)
                            lineTo(xCenter, size.height)
                            lineTo(xCenter + halfTail, rectBottom)
                            close()
                        }
                    }
                }
                op(this, tailPath, androidx.compose.ui.graphics.PathOperation.Union)
            }
        }
        return Outline.Generic(path)
    }
}
