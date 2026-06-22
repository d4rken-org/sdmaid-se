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

val SdmIcons.OsMac: ImageVector
    get() {
        _osMac?.let { return it }
        return ImageVector.Builder(
            name = "OsMac",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(0f, 14.727f)
                horizontalLineToRelative(0.941f)
                verticalLineToRelative(-2.453f)
                curveToRelative(0f, -0.484f, 0.318f, -0.835f, 0.771f, -0.835f)
                curveToRelative(0.439f, 0f, 0.71f, 0.276f, 0.71f, 0.722f)
                verticalLineToRelative(2.566f)
                horizontalLineToRelative(0.915f)
                verticalLineTo(12.25f)
                curveToRelative(0f, -0.48f, 0.31f, -0.812f, 0.764f, -0.812f)
                curveToRelative(0.46f, 0f, 0.718f, 0.28f, 0.718f, 0.77f)
                verticalLineToRelative(2.518f)
                horizontalLineToRelative(0.94f)
                verticalLineToRelative(-2.748f)
                curveToRelative(0f, -0.801f, -0.517f, -1.334f, -1.307f, -1.334f)
                curveToRelative(-0.578f, 0f, -1.054f, 0.31f, -1.247f, 0.805f)
                horizontalLineToRelative(-0.023f)
                curveToRelative(-0.147f, -0.514f, -0.552f, -0.805f, -1.118f, -0.805f)
                curveToRelative(-0.545f, 0f, -0.968f, 0.306f, -1.142f, 0.771f)
                horizontalLineTo(0.903f)
                verticalLineToRelative(-0.695f)
                horizontalLineTo(0f)
                verticalLineToRelative(4.006f)
                close()
                moveToRelative(7.82f, -0.646f)
                curveToRelative(-0.408f, 0f, -0.68f, -0.208f, -0.68f, -0.537f)
                curveToRelative(0f, -0.318f, 0.26f, -0.522f, 0.714f, -0.552f)
                lineToRelative(0.926f, -0.057f)
                verticalLineToRelative(0.307f)
                curveToRelative(0f, 0.483f, -0.427f, 0.839f, -0.96f, 0.839f)
                close()
                moveToRelative(-0.284f, 0.71f)
                curveToRelative(0.514f, 0f, 1.017f, -0.268f, 1.248f, -0.703f)
                horizontalLineToRelative(0.018f)
                verticalLineToRelative(0.639f)
                horizontalLineToRelative(0.908f)
                verticalLineToRelative(-2.76f)
                curveToRelative(0f, -0.804f, -0.647f, -1.33f, -1.64f, -1.33f)
                curveToRelative(-1.021f, 0f, -1.66f, 0.537f, -1.701f, 1.285f)
                horizontalLineToRelative(0.873f)
                curveToRelative(0.06f, -0.332f, 0.344f, -0.548f, 0.79f, -0.548f)
                curveToRelative(0.464f, 0f, 0.748f, 0.242f, 0.748f, 0.662f)
                verticalLineToRelative(0.287f)
                lineToRelative(-1.058f, 0.06f)
                curveToRelative(-0.976f, 0.061f, -1.524f, 0.488f, -1.524f, 1.199f)
                curveToRelative(0f, 0.721f, 0.564f, 1.209f, 1.338f, 1.209f)
                close()
                moveToRelative(6.305f, -2.642f)
                curveToRelative(-0.065f, -0.843f, -0.719f, -1.512f, -1.777f, -1.512f)
                curveToRelative(-1.164f, 0f, -1.92f, 0.805f, -1.92f, 2.087f)
                curveToRelative(0f, 1.3f, 0.756f, 2.082f, 1.928f, 2.082f)
                curveToRelative(1.005f, 0f, 1.697f, -0.59f, 1.772f, -1.485f)
                horizontalLineToRelative(-0.888f)
                curveToRelative(-0.087f, 0.453f, -0.397f, 0.725f, -0.873f, 0.725f)
                curveToRelative(-0.597f, 0f, -0.982f, -0.483f, -0.982f, -1.322f)
                curveToRelative(0f, -0.824f, 0.381f, -1.323f, 0.975f, -1.323f)
                curveToRelative(0.502f, 0f, 0.8f, 0.321f, 0.876f, 0.748f)
                horizontalLineToRelative(0.889f)
                close()
                moveToRelative(2.906f, -2.967f)
                curveToRelative(-1.591f, 0f, -2.589f, 1.085f, -2.589f, 2.82f)
                curveToRelative(0f, 1.735f, 0.998f, 2.816f, 2.59f, 2.816f)
                curveToRelative(1.586f, 0f, 2.584f, -1.081f, 2.584f, -2.816f)
                curveToRelative(0f, -1.735f, -0.997f, -2.82f, -2.585f, -2.82f)
                close()
                moveToRelative(0f, 0.832f)
                curveToRelative(0.971f, 0f, 1.591f, 0.77f, 1.591f, 1.988f)
                curveToRelative(0f, 1.213f, -0.62f, 1.984f, -1.59f, 1.984f)
                curveToRelative(-0.976f, 0f, -1.592f, -0.77f, -1.592f, -1.984f)
                curveToRelative(0f, -1.217f, 0.616f, -1.988f, 1.591f, -1.988f)
                close()
                moveToRelative(2.982f, 3.178f)
                curveToRelative(0.042f, 1.006f, 0.866f, 1.626f, 2.12f, 1.626f)
                curveToRelative(1.32f, 0f, 2.151f, -0.65f, 2.151f, -1.686f)
                curveToRelative(0f, -0.813f, -0.469f, -1.27f, -1.576f, -1.523f)
                lineToRelative(-0.627f, -0.144f)
                curveToRelative(-0.67f, -0.158f, -0.945f, -0.37f, -0.945f, -0.733f)
                curveToRelative(0f, -0.453f, 0.415f, -0.756f, 1.032f, -0.756f)
                curveToRelative(0.623f, 0f, 1.05f, 0.306f, 1.096f, 0.817f)
                horizontalLineToRelative(0.93f)
                curveToRelative(-0.023f, -0.96f, -0.817f, -1.61f, -2.019f, -1.61f)
                curveToRelative(-1.187f, 0f, -2.03f, 0.653f, -2.03f, 1.62f)
                curveToRelative(0f, 0.78f, 0.477f, 1.263f, 1.482f, 1.494f)
                lineToRelative(0.707f, 0.166f)
                curveToRelative(0.688f, 0.163f, 0.967f, 0.39f, 0.967f, 0.782f)
                curveToRelative(0f, 0.454f, -0.457f, 0.779f, -1.115f, 0.779f)
                curveToRelative(-0.665f, 0f, -1.167f, -0.329f, -1.228f, -0.832f)
                horizontalLineToRelative(-0.945f)
                close()
            }
        }.build().also { _osMac = it }
    }

@Preview2
@Composable
private fun OsMacPreview() {
    PreviewWrapper {
        Icon(
            imageVector = SdmIcons.OsMac,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _osMac: ImageVector? = null
