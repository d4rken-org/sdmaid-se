package eu.darken.sdmse.common.compose.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

val SdmIcons.ContainEnd: ImageVector
    get() {
        _containEnd?.let { return it }
        return ImageVector.Builder(
            name = "ContainEnd",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(7f, 17f)
                verticalLineTo(15f)
                horizontalLineTo(9f)
                verticalLineTo(17f)
                horizontalLineTo(7f)
                moveTo(11f, 17f)
                verticalLineTo(15f)
                horizontalLineTo(13f)
                verticalLineTo(17f)
                horizontalLineTo(11f)
                moveTo(15f, 17f)
                verticalLineTo(15f)
                horizontalLineTo(17f)
                verticalLineTo(17f)
                horizontalLineTo(15f)
                moveTo(22f, 3f)
                verticalLineTo(21f)
                horizontalLineTo(16f)
                verticalLineTo(19f)
                horizontalLineTo(20f)
                verticalLineTo(5f)
                horizontalLineTo(16f)
                verticalLineTo(3f)
                horizontalLineTo(22f)
                close()
            }
        }.build().also { _containEnd = it }
    }

@Preview2
@Composable
private fun ContainEndPreview() {
    PreviewWrapper {
        Icon(
            imageVector = SdmIcons.ContainEnd,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _containEnd: ImageVector? = null
