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

val SdmIcons.Contain: ImageVector
    get() {
        _contain?.let { return it }
        return ImageVector.Builder(
            name = "Contain",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(2f, 3f)
                horizontalLineTo(8f)
                verticalLineTo(5f)
                horizontalLineTo(4f)
                verticalLineTo(19f)
                horizontalLineTo(8f)
                verticalLineTo(21f)
                horizontalLineTo(2f)
                verticalLineTo(3f)
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
        }.build().also { _contain = it }
    }

@Preview2
@Composable
private fun ContainPreview() {
    PreviewWrapper {
        Icon(
            imageVector = SdmIcons.Contain,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _contain: ImageVector? = null
