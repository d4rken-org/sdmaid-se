package eu.darken.sdmse.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SdmIcons.Discord: ImageVector
    get() {
        _discord?.let { return it }
        return ImageVector.Builder(
            name = "Discord",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(13.545f, 2.907f)
                arcToRelative(13.2f, 13.2f, 0f, false, false, -3.257f, -1.011f)
                arcToRelative(0.05f, 0.05f, 0f, false, false, -0.052f, 0.025f)
                curveToRelative(-0.141f, 0.25f, -0.297f, 0.577f, -0.406f, 0.833f)
                arcToRelative(12.2f, 12.2f, 0f, false, false, -3.658f, 0f)
                arcToRelative(8f, 8f, 0f, false, false, -0.412f, -0.833f)
                arcToRelative(0.05f, 0.05f, 0f, false, false, -0.052f, -0.025f)
                curveToRelative(-1.125f, 0.194f, -2.22f, 0.534f, -3.257f, 1.011f)
                arcToRelative(0.04f, 0.04f, 0f, false, false, -0.021f, 0.018f)
                curveTo(0.356f, 6.024f, -0.213f, 9.047f, 0.066f, 12.032f)
                quadToRelative(0.003f, 0.022f, 0.021f, 0.037f)
                arcToRelative(13.3f, 13.3f, 0f, false, false, 3.995f, 2.02f)
                arcToRelative(0.05f, 0.05f, 0f, false, false, 0.056f, -0.019f)
                quadToRelative(0.463f, -0.63f, 0.818f, -1.329f)
                arcToRelative(0.05f, 0.05f, 0f, false, false, -0.01f, -0.059f)
                lineToRelative(-0.018f, -0.011f)
                arcToRelative(9f, 9f, 0f, false, true, -1.248f, -0.595f)
                arcToRelative(0.05f, 0.05f, 0f, false, true, -0.02f, -0.066f)
                lineToRelative(0.015f, -0.019f)
                quadToRelative(0.127f, -0.095f, 0.248f, -0.195f)
                arcToRelative(0.05f, 0.05f, 0f, false, true, 0.051f, -0.007f)
                curveToRelative(2.619f, 1.196f, 5.454f, 1.196f, 8.041f, 0f)
                arcToRelative(0.05f, 0.05f, 0f, false, true, 0.053f, 0.007f)
                quadToRelative(0.121f, 0.1f, 0.248f, 0.195f)
                arcToRelative(0.05f, 0.05f, 0f, false, true, -0.004f, 0.085f)
                arcToRelative(8f, 8f, 0f, false, true, -1.249f, 0.594f)
                arcToRelative(0.05f, 0.05f, 0f, false, false, -0.03f, 0.03f)
                arcToRelative(0.05f, 0.05f, 0f, false, false, 0.003f, 0.041f)
                curveToRelative(0.24f, 0.465f, 0.515f, 0.909f, 0.817f, 1.329f)
                arcToRelative(0.05f, 0.05f, 0f, false, false, 0.056f, 0.019f)
                arcToRelative(13.2f, 13.2f, 0f, false, false, 4.001f, -2.02f)
                arcToRelative(0.05f, 0.05f, 0f, false, false, 0.021f, -0.037f)
                curveToRelative(0.334f, -3.451f, -0.559f, -6.449f, -2.366f, -9.106f)
                arcToRelative(0.03f, 0.03f, 0f, false, false, -0.02f, -0.019f)
                moveToRelative(-8.198f, 7.307f)
                curveToRelative(-0.789f, 0f, -1.438f, -0.724f, -1.438f, -1.612f)
                reflectiveCurveToRelative(0.637f, -1.613f, 1.438f, -1.613f)
                curveToRelative(0.807f, 0f, 1.45f, 0.73f, 1.438f, 1.613f)
                curveToRelative(0f, 0.888f, -0.637f, 1.612f, -1.438f, 1.612f)
                moveToRelative(5.316f, 0f)
                curveToRelative(-0.788f, 0f, -1.438f, -0.724f, -1.438f, -1.612f)
                reflectiveCurveToRelative(0.637f, -1.613f, 1.438f, -1.613f)
                curveToRelative(0.807f, 0f, 1.451f, 0.73f, 1.438f, 1.613f)
                curveToRelative(0f, 0.888f, -0.631f, 1.612f, -1.438f, 1.612f)
            }
        }.build().also { _discord = it }
    }
private var _discord: ImageVector? = null
