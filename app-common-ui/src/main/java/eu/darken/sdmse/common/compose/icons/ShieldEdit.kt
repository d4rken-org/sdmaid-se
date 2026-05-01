package eu.darken.sdmse.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SdmIcons.ShieldEdit: ImageVector
    get() {
        _shieldEdit?.let { return it }
        return ImageVector.Builder(
            name = "ShieldEdit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(21.7f, 14.4f)
                lineTo(20.7f, 15.4f)
                lineTo(18.6f, 13.3f)
                lineTo(19.6f, 12.3f)
                curveTo(19.8f, 12.1f, 20.2f, 12.1f, 20.4f, 12.3f)
                lineTo(21.7f, 13.6f)
                curveTo(21.9f, 13.8f, 21.9f, 14.1f, 21.7f, 14.4f)
                moveTo(12f, 19.9f)
                lineTo(18.1f, 13.8f)
                lineTo(20.2f, 15.9f)
                lineTo(14.1f, 22f)
                horizontalLineTo(12f)
                verticalLineTo(19.9f)
                moveTo(10f, 19.1f)
                lineTo(21f, 8.1f)
                verticalLineTo(5f)
                lineTo(12f, 1f)
                lineTo(3f, 5f)
                verticalLineTo(11f)
                curveTo(3f, 15.8f, 5.9f, 20.3f, 10f, 22.3f)
                verticalLineTo(19.1f)
                close()
            }
        }.build().also { _shieldEdit = it }
    }
private var _shieldEdit: ImageVector? = null
