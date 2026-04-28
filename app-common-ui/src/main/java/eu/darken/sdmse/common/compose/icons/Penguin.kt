package eu.darken.sdmse.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SdmIcons.Penguin: ImageVector
    get() {
        _penguin?.let { return it }
        return ImageVector.Builder(
            name = "Penguin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(19f, 16f)
                curveTo(19f, 17.72f, 18.37f, 19.3f, 17.34f, 20.5f)
                curveTo(17.75f, 20.89f, 18f, 21.41f, 18f, 22f)
                horizontalLineTo(6f)
                curveTo(6f, 21.41f, 6.25f, 20.89f, 6.66f, 20.5f)
                curveTo(5.63f, 19.3f, 5f, 17.72f, 5f, 16f)
                horizontalLineTo(3f)
                curveTo(3f, 14.75f, 3.57f, 13.64f, 4.46f, 12.91f)
                lineTo(4.47f, 12.89f)
                curveTo(6f, 11.81f, 7f, 10f, 7f, 8f)
                verticalLineTo(7f)
                arcTo(5f, 5f, 0f, false, true, 12f, 2f)
                arcTo(5f, 5f, 0f, false, true, 17f, 7f)
                verticalLineTo(8f)
                curveTo(17f, 10f, 18f, 11.81f, 19.53f, 12.89f)
                lineTo(19.54f, 12.91f)
                curveTo(20.43f, 13.64f, 21f, 14.75f, 21f, 16f)
                horizontalLineTo(19f)
                moveTo(16f, 16f)
                arcTo(4f, 4f, 0f, false, false, 12f, 12f)
                arcTo(4f, 4f, 0f, false, false, 8f, 16f)
                arcTo(4f, 4f, 0f, false, false, 12f, 20f)
                arcTo(4f, 4f, 0f, false, false, 16f, 16f)
                moveTo(10f, 9f)
                lineTo(12f, 10.5f)
                lineTo(14f, 9f)
                lineTo(12f, 7.5f)
                lineTo(10f, 9f)
                moveTo(10f, 5f)
                arcTo(1f, 1f, 0f, false, false, 9f, 6f)
                arcTo(1f, 1f, 0f, false, false, 10f, 7f)
                arcTo(1f, 1f, 0f, false, false, 11f, 6f)
                arcTo(1f, 1f, 0f, false, false, 10f, 5f)
                moveTo(14f, 5f)
                arcTo(1f, 1f, 0f, false, false, 13f, 6f)
                arcTo(1f, 1f, 0f, false, false, 14f, 7f)
                arcTo(1f, 1f, 0f, false, false, 15f, 6f)
                arcTo(1f, 1f, 0f, false, false, 14f, 5f)
                close()
            }
        }.build().also { _penguin = it }
    }
private var _penguin: ImageVector? = null
