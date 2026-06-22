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

val SdmIcons.GooglePlay: ImageVector
    get() {
        _googlePlay?.let { return it }
        return ImageVector.Builder(
            name = "GooglePlay",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(14.222f, 9.374f)
                curveToRelative(1.037f, -0.61f, 1.037f, -2.137f, 0f, -2.748f)
                lineTo(11.528f, 5.04f)
                lineTo(8.32f, 8f)
                lineToRelative(3.207f, 2.96f)
                close()
                moveToRelative(-3.595f, 2.116f)
                lineTo(7.583f, 8.68f)
                lineTo(1.03f, 14.73f)
                curveToRelative(0.201f, 1.029f, 1.36f, 1.61f, 2.303f, 1.055f)
                close()
                moveTo(1f, 13.396f)
                verticalLineTo(2.603f)
                lineTo(6.846f, 8f)
                close()
                moveTo(1.03f, 1.27f)
                lineToRelative(6.553f, 6.05f)
                lineToRelative(3.044f, -2.81f)
                lineTo(3.333f, 0.215f)
                curveTo(2.39f, -0.341f, 1.231f, 0.24f, 1.03f, 1.27f)
            }
        }.build().also { _googlePlay = it }
    }

@Preview2
@Composable
private fun GooglePlayPreview() {
    PreviewWrapper {
        Icon(
            imageVector = SdmIcons.GooglePlay,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _googlePlay: ImageVector? = null
