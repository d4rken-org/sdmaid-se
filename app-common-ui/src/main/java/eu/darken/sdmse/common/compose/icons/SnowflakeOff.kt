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

val SdmIcons.SnowflakeOff: ImageVector
    get() {
        _snowflakeOff?.let { return it }
        return ImageVector.Builder(
            name = "SnowflakeOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(11f, 5.12f)
                lineTo(9.29f, 3.41f)
                lineTo(10.71f, 2f)
                lineTo(12f, 3.29f)
                lineTo(13.29f, 2f)
                lineTo(14.71f, 3.41f)
                lineTo(13f, 5.12f)
                verticalLineTo(7.38f)
                lineTo(15.45f, 8.82f)
                lineTo(17.45f, 7.69f)
                lineTo(18.07f, 5.36f)
                lineTo(20f, 5.88f)
                lineTo(19.54f, 7.65f)
                lineTo(21.31f, 8.12f)
                lineTo(20.79f, 10.05f)
                lineTo(18.46f, 9.43f)
                lineTo(16.46f, 10.56f)
                verticalLineTo(13.26f)
                lineTo(14.5f, 11.3f)
                verticalLineTo(10.56f)
                lineTo(12.74f, 9.54f)
                lineTo(10.73f, 7.53f)
                lineTo(11f, 7.38f)
                verticalLineTo(5.12f)
                moveTo(18.46f, 14.57f)
                lineTo(16.87f, 13.67f)
                lineTo(19.55f, 16.35f)
                lineTo(21.3f, 15.88f)
                lineTo(20.79f, 13.95f)
                lineTo(18.46f, 14.57f)
                moveTo(13f, 16.62f)
                verticalLineTo(18.88f)
                lineTo(14.7f, 20.59f)
                lineTo(13.29f, 22f)
                lineTo(12f, 20.71f)
                lineTo(10.71f, 22f)
                lineTo(9.29f, 20.59f)
                lineTo(11f, 18.88f)
                verticalLineTo(16.62f)
                lineTo(8.55f, 15.18f)
                lineTo(6.55f, 16.31f)
                lineTo(5.93f, 18.64f)
                lineTo(4f, 18.12f)
                lineTo(4.47f, 16.36f)
                lineTo(2.7f, 15.89f)
                lineTo(3.22f, 13.96f)
                lineTo(5.55f, 14.58f)
                lineTo(7.55f, 13.45f)
                verticalLineTo(10.56f)
                lineTo(5.55f, 9.43f)
                lineTo(3.22f, 10.05f)
                lineTo(2.7f, 8.12f)
                lineTo(4.47f, 7.65f)
                lineTo(4f, 5.89f)
                lineTo(1.11f, 3f)
                lineTo(2.39f, 1.73f)
                lineTo(22.11f, 21.46f)
                lineTo(20.84f, 22.73f)
                lineTo(14.1f, 16f)
                lineTo(13f, 16.62f)
                moveTo(12f, 14.89f)
                lineTo(12.63f, 14.5f)
                lineTo(9.5f, 11.39f)
                verticalLineTo(13.44f)
                lineTo(12f, 14.89f)
                close()
            }
        }.build().also { _snowflakeOff = it }
    }

@Preview2
@Composable
private fun SnowflakeOffPreview() {
    PreviewWrapper {
        Icon(
            imageVector = SdmIcons.SnowflakeOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _snowflakeOff: ImageVector? = null
