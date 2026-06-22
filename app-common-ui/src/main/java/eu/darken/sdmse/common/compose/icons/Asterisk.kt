package eu.darken.sdmse.common.compose.icons

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

val SdmIcons.Asterisk: ImageVector
    get() {
        _asterisk?.let { return it }
        return ImageVector.Builder(
            name = "Asterisk",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            for (rotation in listOf(0f, 60f, 120f)) {
                group(rotate = rotation, pivotX = 12f, pivotY = 12f) {
                    path(
                        fill = SolidColor(Color.Black),
                        fillAlpha = 0.3f,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 1.2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(10.5f, 5f)
                        arcTo(1.5f, 1.5f, 0f, false, true, 13.5f, 5f)
                        lineTo(13.5f, 19f)
                        arcTo(1.5f, 1.5f, 0f, false, true, 10.5f, 19f)
                        close()
                    }
                }
            }
        }.build().also { _asterisk = it }
    }

@Preview2
@Composable
private fun AsteriskPreview() {
    PreviewWrapper {
        Row {
            Icon(imageVector = SdmIcons.Asterisk, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Icon(imageVector = SdmIcons.Asterisk, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Icon(imageVector = SdmIcons.Asterisk, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Icon(imageVector = SdmIcons.Asterisk, contentDescription = null, modifier = Modifier.size(96.dp))
        }
    }
}

private var _asterisk: ImageVector? = null
