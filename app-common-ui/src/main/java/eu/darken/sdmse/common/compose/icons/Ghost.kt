package eu.darken.sdmse.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SdmIcons.Ghost: ImageVector
    get() {
        _ghost?.let { return it }
        return ImageVector.Builder(
            name = "Ghost",
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
                moveTo(5f, 11f)
                arcToRelative(7f, 7f, 0f, false, true, 14f, 0f)
                verticalLineToRelative(7f)
                arcToRelative(1.78f, 1.78f, 0f, false, true, -3.1f, 1.4f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, -2.6f, 0f)
                arcToRelative(1.65f, 1.65f, 0f, false, true, -2.6f, 0f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, -2.6f, 0f)
                arcToRelative(1.78f, 1.78f, 0f, false, true, -3.1f, -1.4f)
                verticalLineToRelative(-7f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10f, 10f)
                lineToRelative(0.01f, 0f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(14f, 10f)
                lineToRelative(0.01f, 0f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10f, 14f)
                arcToRelative(3.5f, 3.5f, 0f, false, false, 4f, 0f)
            }
        }.build().also { _ghost = it }
    }
private var _ghost: ImageVector? = null
