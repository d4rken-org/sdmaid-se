package eu.darken.sdmse.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SdmIcons.ApproximatelyEqualBox: ImageVector
    get() {
        _approximatelyEqualBox?.let { return it }
        return ImageVector.Builder(
            name = "ApproximatelyEqualBox",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(208f, 32f)
                horizontalLineTo(48f)
                arcTo(16f, 16f, 0f, false, false, 32f, 48f)
                verticalLineTo(208f)
                arcToRelative(16f, 16f, 0f, false, false, 16f, 16f)
                horizontalLineTo(208f)
                arcToRelative(16f, 16f, 0f, false, false, 16f, -16f)
                verticalLineTo(48f)
                arcTo(16f, 16f, 0f, false, false, 208f, 32f)
                close()
                moveTo(197.2f, 160.87f)
                curveToRelative(-13.07f, 11.18f, -24.9f, 15.1f, -35.64f, 15.1f)
                curveToRelative(-14.26f, 0f, -26.62f, -6.92f, -37.47f, -13f)
                curveToRelative(-18.41f, -10.31f, -32.95f, -18.45f, -54.89f, 0.31f)
                arcToRelative(8f, 8f, 0f, true, true, -10.4f, -12.16f)
                curveToRelative(30.42f, -26f, 54.09f, -12.76f, 73.11f, -2.11f)
                curveToRelative(18.41f, 10.31f, 33f, 18.45f, 54.89f, -0.31f)
                arcToRelative(8f, 8f, 0f, false, true, 10.4f, 12.16f)
                close()
                moveToRelative(0f, -56f)
                curveToRelative(-13.07f, 11.18f, -24.9f, 15.1f, -35.64f, 15.1f)
                curveToRelative(-14.26f, 0f, -26.62f, -6.92f, -37.47f, -13f)
                curveToRelative(-18.41f, -10.31f, -32.95f, -18.45f, -54.89f, 0.31f)
                arcTo(8f, 8f, 0f, false, true, 58.8f, 95.13f)
                curveToRelative(30.42f, -26f, 54.09f, -12.76f, 73.11f, -2.11f)
                curveToRelative(18.41f, 10.31f, 33f, 18.45f, 54.89f, -0.31f)
                arcToRelative(8f, 8f, 0f, true, true, 10.4f, 12.16f)
                close()
            }
        }.build().also { _approximatelyEqualBox = it }
    }
private var _approximatelyEqualBox: ImageVector? = null
