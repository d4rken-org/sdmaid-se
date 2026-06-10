package eu.darken.sdmse.common.compose.shimmer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Animated loading placeholder: a highlight band sweeping across a tonal background.
 * Use on fixed-size boxes standing in for values that haven't loaded yet.
 */
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(4.dp)): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "shimmerProgress",
    )
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.onSurfaceVariant
        .copy(alpha = 0.15f)
        .compositeOver(baseColor)

    clip(shape).drawBehind {
        drawRect(baseColor)
        val band = size.width
        val start = progress * (size.width + band) - band
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlightColor, Color.Transparent),
                start = Offset(start, 0f),
                end = Offset(start + band, size.height),
            ),
        )
    }
}

@Composable
fun ShimmerLine(
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    height: Dp = 14.dp,
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .shimmer(),
    )
}

@Preview2
@Composable
private fun ShimmerLinePreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            ShimmerLine()
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerLine(width = 200.dp, height = 20.dp)
        }
    }
}
