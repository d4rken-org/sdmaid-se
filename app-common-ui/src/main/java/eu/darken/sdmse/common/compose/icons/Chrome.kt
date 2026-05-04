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

val SdmIcons.Chrome: ImageVector
    get() {
        _chrome?.let { return it }
        return ImageVector.Builder(
            name = "Chrome",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(16f, 8f)
                arcToRelative(8f, 8f, 0f, false, true, -7.022f, 7.94f)
                lineToRelative(1.902f, -7.098f)
                arcToRelative(3f, 3f, 0f, false, false, 0.05f, -1.492f)
                arcTo(3f, 3f, 0f, false, false, 10.237f, 6f)
                horizontalLineToRelative(5.511f)
                arcTo(8f, 8f, 0f, false, true, 16f, 8f)
                moveTo(0f, 8f)
                arcToRelative(8f, 8f, 0f, false, false, 7.927f, 8f)
                lineToRelative(1.426f, -5.321f)
                arcToRelative(3f, 3f, 0f, false, true, -0.723f, 0.255f)
                arcToRelative(3f, 3f, 0f, false, true, -1.743f, -0.147f)
                arcToRelative(3f, 3f, 0f, false, true, -1.043f, -0.7f)
                lineTo(0.633f, 4.876f)
                arcTo(8f, 8f, 0f, false, false, 0f, 8f)
                moveToRelative(5.004f, -0.167f)
                lineTo(1.108f, 3.936f)
                arcTo(8.003f, 8.003f, 0f, false, true, 15.418f, 5f)
                horizontalLineTo(8.066f)
                arcToRelative(3f, 3f, 0f, false, false, -1.252f, 0.243f)
                arcToRelative(2.99f, 2.99f, 0f, false, false, -1.81f, 2.59f)
                moveTo(8f, 10f)
                arcToRelative(2f, 2f, 0f, true, false, 0f, -4f)
                arcToRelative(2f, 2f, 0f, false, false, 0f, 4f)
            }
        }.build().also { _chrome = it }
    }

@Preview2
@Composable
private fun ChromePreview() {
    PreviewWrapper {
        Icon(
            imageVector = SdmIcons.Chrome,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _chrome: ImageVector? = null
