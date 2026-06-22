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

val SdmIcons.Qq: ImageVector
    get() {
        _qq?.let { return it }
        return ImageVector.Builder(
            name = "Qq",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(6.048f, 3.323f)
                curveToRelative(0.022f, 0.277f, -0.13f, 0.523f, -0.338f, 0.55f)
                curveToRelative(-0.21f, 0.026f, -0.397f, -0.176f, -0.419f, -0.453f)
                reflectiveCurveToRelative(0.13f, -0.523f, 0.338f, -0.55f)
                curveToRelative(0.21f, -0.026f, 0.397f, 0.176f, 0.42f, 0.453f)
                close()
                moveToRelative(2.265f, -0.24f)
                curveToRelative(-0.603f, -0.146f, -0.894f, 0.256f, -0.936f, 0.333f)
                curveToRelative(-0.027f, 0.048f, -0.008f, 0.117f, 0.037f, 0.15f)
                curveToRelative(0.045f, 0.035f, 0.092f, 0.025f, 0.119f, -0.003f)
                curveToRelative(0.361f, -0.39f, 0.751f, -0.172f, 0.829f, -0.129f)
                lineToRelative(0.011f, 0.007f)
                curveToRelative(0.053f, 0.024f, 0.147f, 0.028f, 0.193f, -0.098f)
                curveToRelative(0.023f, -0.063f, 0.017f, -0.11f, -0.006f, -0.142f)
                curveToRelative(-0.016f, -0.023f, -0.089f, -0.08f, -0.247f, -0.118f)
            }
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(11.727f, 6.719f)
                curveToRelative(0f, -0.022f, 0.01f, -0.375f, 0.01f, -0.557f)
                curveToRelative(0f, -3.07f, -1.45f, -6.156f, -5.015f, -6.156f)
                reflectiveCurveTo(1.708f, 3.092f, 1.708f, 6.162f)
                curveToRelative(0f, 0.182f, 0.01f, 0.535f, 0.01f, 0.557f)
                lineToRelative(-0.72f, 1.795f)
                arcToRelative(26f, 26f, 0f, false, false, -0.534f, 1.508f)
                curveToRelative(-0.68f, 2.187f, -0.46f, 3.093f, -0.292f, 3.113f)
                curveToRelative(0.36f, 0.044f, 1.401f, -1.647f, 1.401f, -1.647f)
                curveToRelative(0f, 0.979f, 0.504f, 2.256f, 1.594f, 3.179f)
                curveToRelative(-0.408f, 0.126f, -0.907f, 0.319f, -1.228f, 0.556f)
                curveToRelative(-0.29f, 0.213f, -0.253f, 0.43f, -0.201f, 0.518f)
                curveToRelative(0.228f, 0.386f, 3.92f, 0.246f, 4.985f, 0.126f)
                curveToRelative(1.065f, 0.12f, 4.756f, 0.26f, 4.984f, -0.126f)
                curveToRelative(0.052f, -0.088f, 0.088f, -0.305f, -0.2f, -0.518f)
                curveToRelative(-0.322f, -0.237f, -0.822f, -0.43f, -1.23f, -0.557f)
                curveToRelative(1.09f, -0.922f, 1.594f, -2.2f, 1.594f, -3.178f)
                curveToRelative(0f, 0f, 1.041f, 1.69f, 1.401f, 1.647f)
                curveToRelative(0.168f, -0.02f, 0.388f, -0.926f, -0.292f, -3.113f)
                arcToRelative(26f, 26f, 0f, false, false, -0.534f, -1.508f)
                lineToRelative(-0.72f, -1.795f)
                close()
                moveTo(9.773f, 5.53f)
                arcToRelative(0.1f, 0.1f, 0f, false, true, -0.009f, 0.096f)
                curveToRelative(-0.109f, 0.159f, -1.554f, 0.943f, -3.033f, 0.943f)
                horizontalLineToRelative(-0.017f)
                curveToRelative(-1.48f, 0f, -2.925f, -0.784f, -3.034f, -0.943f)
                arcToRelative(0.1f, 0.1f, 0f, false, true, -0.018f, -0.055f)
                quadToRelative(0f, -0.022f, 0.01f, -0.04f)
                curveToRelative(0.13f, -0.287f, 1.43f, -0.606f, 3.042f, -0.606f)
                horizontalLineToRelative(0.017f)
                curveToRelative(1.611f, 0f, 2.912f, 0.319f, 3.042f, 0.605f)
                moveToRelative(-4.32f, -0.989f)
                curveToRelative(-0.483f, 0.022f, -0.896f, -0.529f, -0.922f, -1.229f)
                reflectiveCurveToRelative(0.344f, -1.286f, 0.828f, -1.308f)
                curveToRelative(0.483f, -0.022f, 0.896f, 0.529f, 0.922f, 1.23f)
                curveToRelative(0.027f, 0.7f, -0.344f, 1.286f, -0.827f, 1.307f)
                close()
                moveToRelative(2.538f, 0f)
                curveToRelative(-0.484f, -0.022f, -0.854f, -0.607f, -0.828f, -1.308f)
                curveToRelative(0.027f, -0.7f, 0.44f, -1.25f, 0.923f, -1.23f)
                curveToRelative(0.483f, 0.023f, 0.853f, 0.608f, 0.827f, 1.309f)
                curveToRelative(-0.026f, 0.7f, -0.439f, 1.251f, -0.922f, 1.23f)
                close()
                moveTo(2.928f, 8.99f)
                quadToRelative(0.32f, 0.063f, 0.639f, 0.117f)
                verticalLineToRelative(2.336f)
                reflectiveCurveToRelative(1.104f, 0.222f, 2.21f, 0.068f)
                verticalLineTo(9.363f)
                quadToRelative(0.49f, 0.027f, 0.937f, 0.023f)
                horizontalLineToRelative(0.017f)
                curveToRelative(1.117f, 0.013f, 2.474f, -0.136f, 3.786f, -0.396f)
                curveToRelative(0.097f, 0.622f, 0.151f, 1.386f, 0.097f, 2.284f)
                curveToRelative(-0.146f, 2.45f, -1.6f, 3.99f, -3.846f, 4.012f)
                horizontalLineToRelative(-0.091f)
                curveToRelative(-2.245f, -0.023f, -3.7f, -1.562f, -3.846f, -4.011f)
                curveToRelative(-0.054f, -0.9f, 0f, -1.663f, 0.097f, -2.285f)
            }
        }.build().also { _qq = it }
    }

@Preview2
@Composable
private fun QqPreview() {
    PreviewWrapper {
        Icon(
            imageVector = SdmIcons.Qq,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _qq: ImageVector? = null
