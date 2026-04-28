package eu.darken.sdmse.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SdmIcons.Shizuku: ImageVector
    get() {
        _shizuku?.let { return it }
        return ImageVector.Builder(
            name = "Shizuku",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.EvenOdd,
            ) {
                moveTo(12f, 2f)
                lineTo(20.66f, 7f)
                lineTo(20.66f, 17f)
                lineTo(12f, 22f)
                lineTo(3.34f, 17f)
                lineTo(3.34f, 7f)
                close()
                moveTo(12f, 5f)
                lineTo(18.06f, 8.5f)
                lineTo(18.06f, 15.5f)
                lineTo(12f, 19f)
                lineTo(5.94f, 15.5f)
                lineTo(5.94f, 8.5f)
                close()
                moveTo(10.5f, 12f)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 13.5f, 12f)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 10.5f, 12f)
                close()
            }
        }.build().also { _shizuku = it }
    }
private var _shizuku: ImageVector? = null
