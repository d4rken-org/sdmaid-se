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

val SdmIcons.Numeric0Box: ImageVector
    get() {
        _numeric0Box?.let { return it }
        return ImageVector.Builder(
            name = "Numeric0Box",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(19f, 3f)
                arcTo(2f, 2f, 0f, false, true, 21f, 5f)
                verticalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 19f, 21f)
                horizontalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 3f, 19f)
                verticalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                horizontalLineTo(19f)
                moveTo(11f, 7f)
                arcTo(2f, 2f, 0f, false, false, 9f, 9f)
                verticalLineTo(15f)
                arcTo(2f, 2f, 0f, false, false, 11f, 17f)
                horizontalLineTo(13f)
                arcTo(2f, 2f, 0f, false, false, 15f, 15f)
                verticalLineTo(9f)
                arcTo(2f, 2f, 0f, false, false, 13f, 7f)
                horizontalLineTo(11f)
                moveTo(11f, 9f)
                horizontalLineTo(13f)
                verticalLineTo(15f)
                horizontalLineTo(11f)
                verticalLineTo(9f)
                close()
            }
        }.build().also { _numeric0Box = it }
    }

@Preview2
@Composable
private fun Numeric0BoxPreview() {
    PreviewWrapper {
        Icon(
            imageVector = SdmIcons.Numeric0Box,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _numeric0Box: ImageVector? = null
