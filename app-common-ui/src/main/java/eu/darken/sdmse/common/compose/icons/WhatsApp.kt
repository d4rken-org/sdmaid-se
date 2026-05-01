package eu.darken.sdmse.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SdmIcons.WhatsApp: ImageVector
    get() {
        _whatsApp?.let { return it }
        return ImageVector.Builder(
            name = "WhatsApp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(19.05f, 4.91f)
                curveTo(17.18f, 3.03f, 14.69f, 2f, 12.04f, 2f)
                curveToRelative(-5.46f, 0f, -9.91f, 4.45f, -9.91f, 9.91f)
                curveToRelative(0f, 1.75f, 0.46f, 3.45f, 1.32f, 4.95f)
                lineTo(2.05f, 22f)
                lineToRelative(5.25f, -1.38f)
                curveToRelative(1.45f, 0.79f, 3.08f, 1.21f, 4.74f, 1.21f)
                horizontalLineToRelative(0f)
                curveToRelative(0f, 0f, 0f, 0f, 0f, 0f)
                curveToRelative(5.46f, 0f, 9.91f, -4.45f, 9.91f, -9.91f)
                curveTo(21.95f, 9.27f, 20.92f, 6.78f, 19.05f, 4.91f)
                close()
                moveTo(12.04f, 20.15f)
                lineTo(12.04f, 20.15f)
                curveToRelative(-1.48f, 0f, -2.93f, -0.4f, -4.2f, -1.15f)
                lineToRelative(-0.3f, -0.18f)
                lineToRelative(-3.12f, 0.82f)
                lineToRelative(0.83f, -3.04f)
                lineToRelative(-0.2f, -0.31f)
                curveToRelative(-0.82f, -1.31f, -1.26f, -2.83f, -1.26f, -4.38f)
                curveToRelative(0f, -4.54f, 3.7f, -8.24f, 8.24f, -8.24f)
                curveToRelative(2.2f, 0f, 4.27f, 0.86f, 5.82f, 2.42f)
                curveToRelative(1.56f, 1.56f, 2.41f, 3.63f, 2.41f, 5.83f)
                curveTo(20.28f, 16.46f, 16.58f, 20.15f, 12.04f, 20.15f)
                close()
                moveTo(16.56f, 13.99f)
                curveToRelative(-0.25f, -0.12f, -1.47f, -0.72f, -1.69f, -0.81f)
                curveToRelative(-0.23f, -0.08f, -0.39f, -0.12f, -0.56f, 0.12f)
                curveToRelative(-0.17f, 0.25f, -0.64f, 0.81f, -0.78f, 0.97f)
                curveToRelative(-0.14f, 0.17f, -0.29f, 0.19f, -0.54f, 0.06f)
                curveToRelative(-0.25f, -0.12f, -1.05f, -0.39f, -1.99f, -1.23f)
                curveToRelative(-0.74f, -0.66f, -1.23f, -1.47f, -1.38f, -1.72f)
                curveToRelative(-0.14f, -0.25f, -0.02f, -0.38f, 0.11f, -0.51f)
                curveToRelative(0.11f, -0.11f, 0.25f, -0.29f, 0.37f, -0.43f)
                curveToRelative(0.12f, -0.14f, 0.17f, -0.25f, 0.25f, -0.41f)
                curveToRelative(0.08f, -0.17f, 0.04f, -0.31f, -0.02f, -0.43f)
                curveToRelative(-0.06f, -0.12f, -0.56f, -1.34f, -0.76f, -1.84f)
                curveToRelative(-0.2f, -0.48f, -0.41f, -0.42f, -0.56f, -0.43f)
                curveTo(8.86f, 7.33f, 8.7f, 7.33f, 8.53f, 7.33f)
                curveToRelative(-0.17f, 0f, -0.43f, 0.06f, -0.66f, 0.31f)
                curveTo(7.65f, 7.89f, 7.01f, 8.49f, 7.01f, 9.71f)
                curveToRelative(0f, 1.22f, 0.89f, 2.4f, 1.01f, 2.56f)
                curveToRelative(0.12f, 0.17f, 1.75f, 2.67f, 4.23f, 3.74f)
                curveToRelative(0.59f, 0.26f, 1.05f, 0.41f, 1.41f, 0.52f)
                curveToRelative(0.59f, 0.19f, 1.13f, 0.16f, 1.56f, 0.1f)
                curveToRelative(0.48f, -0.07f, 1.47f, -0.6f, 1.67f, -1.18f)
                curveToRelative(0.21f, -0.58f, 0.21f, -1.07f, 0.14f, -1.18f)
                reflectiveCurveTo(16.81f, 14.11f, 16.56f, 13.99f)
                close()
            }
        }.build().also { _whatsApp = it }
    }
private var _whatsApp: ImageVector? = null
