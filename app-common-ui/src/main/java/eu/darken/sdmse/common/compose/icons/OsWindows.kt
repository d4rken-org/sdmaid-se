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

val SdmIcons.OsWindows: ImageVector
    get() {
        _osWindows?.let { return it }
        return ImageVector.Builder(
            name = "OsWindows",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(6.555f, 1.375f)
                lineTo(0f, 2.237f)
                verticalLineToRelative(5.45f)
                horizontalLineToRelative(6.555f)
                close()
                moveTo(0f, 13.795f)
                lineToRelative(6.555f, 0.933f)
                verticalLineTo(8.313f)
                horizontalLineTo(0f)
                close()
                moveToRelative(7.278f, -5.4f)
                lineToRelative(0.026f, 6.378f)
                lineTo(16f, 16f)
                verticalLineTo(8.395f)
                close()
                moveTo(16f, 0f)
                lineTo(7.33f, 1.244f)
                verticalLineToRelative(6.414f)
                horizontalLineTo(16f)
                close()
            }
        }.build().also { _osWindows = it }
    }

@Preview2
@Composable
private fun OsWindowsPreview() {
    PreviewWrapper {
        Icon(
            imageVector = SdmIcons.OsWindows,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _osWindows: ImageVector? = null
