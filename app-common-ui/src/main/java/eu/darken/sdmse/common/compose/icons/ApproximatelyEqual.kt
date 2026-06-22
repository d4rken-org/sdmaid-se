package eu.darken.sdmse.common.compose.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

val SdmIcons.ApproximatelyEqual: ImageVector
    get() {
        _approximatelyEqual?.let { return it }
        return ImageVector.Builder(
            name = "ApproximatelyEqual",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 15f)
                arcToRelative(6.5f, 6.5f, 0f, false, true, 7f, 0f)
                arcToRelative(6.5f, 6.5f, 0f, false, false, 7f, 0f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 9f)
                arcToRelative(6.5f, 6.5f, 0f, false, true, 7f, 0f)
                arcToRelative(6.5f, 6.5f, 0f, false, false, 7f, 0f)
            }
        }.build().also { _approximatelyEqual = it }
    }

@Preview2
@Composable
private fun ApproximatelyEqualPreview() {
    PreviewWrapper {
        Icon(
            imageVector = SdmIcons.ApproximatelyEqual,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _approximatelyEqual: ImageVector? = null
