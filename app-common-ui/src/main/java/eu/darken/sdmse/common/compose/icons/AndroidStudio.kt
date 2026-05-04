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

val SdmIcons.AndroidStudio: ImageVector
    get() {
        _androidStudio?.let { return it }
        return ImageVector.Builder(
            name = "AndroidStudio",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(19.2693f, 10.3368f)
                curveToRelative(-0.3321f, 0f, -0.6026f, 0.2705f, -0.6026f, 0.6031f)
                verticalLineToRelative(9.8324f)
                horizontalLineToRelative(-1.7379f)
                lineToRelative(-3.3355f, -6.9396f)
                curveToRelative(0.476f, -0.5387f, 0.6797f, -1.286f, 0.5243f, -2.0009f)
                arcToRelative(2.2862f, 2.2862f, 0f, false, false, -1.2893f, -1.6248f)
                verticalLineToRelative(-0.8124f)
                curveToRelative(0.0121f, -0.2871f, -0.1426f, -0.5787f, -0.4043f, -0.7407f)
                curveToRelative(-0.1391f, -0.0825f, -0.2884f, -0.1234f, -0.4402f, -0.1234f)
                arcToRelative(0.8478f, 0.8478f, 0f, false, false, -0.4318f, 0.1182f)
                curveToRelative(-0.2701f, 0.1671f, -0.4248f, 0.4587f, -0.4123f, 0.7662f)
                lineToRelative(-0.0003f, 0.721f)
                curveToRelative(-1.0149f, 0.3668f, -1.6619f, 1.4153f, -1.4867f, 2.5197f)
                arcToRelative(2.282f, 2.282f, 0f, false, false, 0.5916f, 1.2103f)
                lineToRelative(-3.2096f, 6.9064f)
                horizontalLineTo(4.0928f)
                curveToRelative(-1.0949f, -0.007f, -1.9797f, -0.8948f, -1.9832f, -1.9896f)
                verticalLineTo(5.016f)
                curveToRelative(-0.0055f, 1.1024f, 0.8836f, 2.0006f, 1.9859f, 2.0062f)
                arcToRelative(2.024f, 2.024f, 0f, false, false, 0.1326f, -0.0037f)
                horizontalLineToRelative(14.7453f)
                reflectiveCurveToRelative(2.5343f, -0.2189f, 2.8619f, 1.5392f)
                curveToRelative(-0.2491f, 0.0287f, -0.4449f, 0.2321f, -0.4449f, 0.4889f)
                curveToRelative(0f, 0.7115f, -0.5791f, 1.2901f, -1.3028f, 1.2901f)
                horizontalLineToRelative(-0.8183f)
                close()
                moveTo(17.222f, 22.5366f)
                curveToRelative(0.2347f, 0.4837f, 0.0329f, 1.066f, -0.4507f, 1.3007f)
                curveToRelative(-0.1296f, 0.0629f, -0.2666f, 0.0895f, -0.4018f, 0.0927f)
                arcToRelative(0.9738f, 0.9738f, 0f, false, true, -0.3194f, -0.0455f)
                curveToRelative(-0.024f, -0.0078f, -0.046f, -0.0209f, -0.0694f, -0.0305f)
                arcToRelative(0.9701f, 0.9701f, 0f, false, true, -0.2277f, -0.1321f)
                curveToRelative(-0.0247f, -0.0192f, -0.0495f, -0.038f, -0.0724f, -0.0598f)
                curveToRelative(-0.0825f, -0.0783f, -0.1574f, -0.1672f, -0.21f, -0.2757f)
                lineToRelative(-1.2554f, -2.6143f)
                lineToRelative(-1.5585f, -3.2452f)
                arcToRelative(0.7725f, 0.7725f, 0f, false, false, -0.6995f, -0.4443f)
                horizontalLineToRelative(-0.0024f)
                arcToRelative(0.792f, 0.792f, 0f, false, false, -0.7083f, 0.4443f)
                lineToRelative(-1.5109f, 3.2452f)
                lineToRelative(-1.2321f, 2.6464f)
                arcToRelative(0.9722f, 0.9722f, 0f, false, true, -0.7985f, 0.5795f)
                curveToRelative(-0.0626f, 0.0053f, -0.1238f, -0.0024f, -0.185f, -0.0087f)
                curveToRelative(-0.0344f, -0.0036f, -0.069f, -0.0053f, -0.1025f, -0.0124f)
                curveToRelative(-0.0489f, -0.0103f, -0.0954f, -0.0278f, -0.142f, -0.0452f)
                curveToRelative(-0.0301f, -0.0113f, -0.0613f, -0.0197f, -0.0901f, -0.0339f)
                curveToRelative(-0.0496f, -0.0244f, -0.0948f, -0.0565f, -0.1397f, -0.0889f)
                curveToRelative(-0.0217f, -0.0156f, -0.0457f, -0.0275f, -0.0662f, -0.045f)
                arcToRelative(0.9862f, 0.9862f, 0f, false, true, -0.1695f, -0.1844f)
                arcToRelative(0.9788f, 0.9788f, 0f, false, true, -0.0708f, -0.9852f)
                lineToRelative(0.8469f, -1.8223f)
                lineToRelative(3.2676f, -7.0314f)
                arcToRelative(1.7964f, 1.7964f, 0f, false, true, -0.7072f, -1.1637f)
                curveToRelative(-0.1555f, -0.9799f, 0.5129f, -1.9003f, 1.4928f, -2.0559f)
                verticalLineTo(9.3946f)
                arcToRelative(0.3542f, 0.3542f, 0f, false, true, 0.1674f, -0.3155f)
                arcToRelative(0.3468f, 0.3468f, 0f, false, true, 0.3541f, 0f)
                arcToRelative(0.354f, 0.354f, 0f, false, true, 0.1674f, 0.3155f)
                verticalLineToRelative(1.159f)
                lineToRelative(0.0129f, 0.0064f)
                arcToRelative(1.8028f, 1.8028f, 0f, false, true, 1.2878f, 1.378f)
                arcToRelative(1.7835f, 1.7835f, 0f, false, true, -0.6439f, 1.7836f)
                lineToRelative(3.3889f, 7.0507f)
                lineToRelative(0.8481f, 1.7643f)
                close()
                moveTo(12.9841f, 12.306f)
                curveToRelative(0.0042f, -0.6081f, -0.4854f, -1.1044f, -1.0935f, -1.1085f)
                arcToRelative(1.1204f, 1.1204f, 0f, false, false, -0.7856f, 0.3219f)
                arcToRelative(1.101f, 1.101f, 0f, false, false, -0.323f, 0.7716f)
                curveToRelative(-0.0042f, 0.6081f, 0.4854f, 1.1044f, 1.0935f, 1.1085f)
                horizontalLineToRelative(0.0077f)
                curveToRelative(0.6046f, 0f, 1.0967f, -0.488f, 1.1009f, -1.0935f)
                close()
                moveToRelative(-1.027f, 5.2768f)
                curveToRelative(-0.1119f, 0.0005f, -0.2121f, 0.0632f, -0.2571f, 0.1553f)
                lineToRelative(-1.4127f, 3.0342f)
                horizontalLineToRelative(3.3733f)
                lineToRelative(-1.4564f, -3.0328f)
                arcToRelative(0.274f, 0.274f, 0f, false, false, -0.2471f, -0.1567f)
                close()
                moveToRelative(8.1432f, -6.7459f)
                lineToRelative(-0.0129f, -0.0001f)
                horizontalLineToRelative(-0.8177f)
                arcToRelative(0.103f, 0.103f, 0f, false, false, -0.103f, 0.103f)
                verticalLineToRelative(12.9103f)
                arcToRelative(0.103f, 0.103f, 0f, false, false, 0.0966f, 0.103f)
                horizontalLineToRelative(0.8435f)
                curveToRelative(0.9861f, -0.0035f, 1.7836f, -0.804f, 1.7836f, -1.79f)
                verticalLineTo(9.0468f)
                curveToRelative(0f, 0.9887f, -0.8014f, 1.7901f, -1.7901f, 1.7901f)
                close()
                moveTo(2.6098f, 5.0161f)
                verticalLineToRelative(0.019f)
                curveToRelative(0.0039f, 0.816f, 0.6719f, 1.483f, 1.4874f, 1.4869f)
                arcToRelative(12.061f, 12.061f, 0f, false, true, 0.1309f, -0.0034f)
                horizontalLineToRelative(1.1286f)
                curveToRelative(0.1972f, -1.315f, 0.7607f, -2.525f, 1.638f, -3.4859f)
                horizontalLineTo(4.0993f)
                curveToRelative(-0.9266f, 0.0031f, -1.6971f, 0.6401f, -1.9191f, 1.4975f)
                curveToRelative(0.2417f, 0.0355f, 0.4296f, 0.235f, 0.4296f, 0.4859f)
                close()
                moveToRelative(6.3381f, -2.8977f)
                lineTo(7.9112f, 0.3284f)
                arcToRelative(0.219f, 0.219f, 0f, false, true, 0f, -0.2189f)
                arcTo(0.2384f, 0.2384f, 0f, false, true, 8.098f, 0f)
                arcToRelative(0.219f, 0.219f, 0f, false, true, 0.1867f, 0.1094f)
                lineToRelative(1.0496f, 1.8158f)
                arcToRelative(6.4907f, 6.4907f, 0f, false, true, 5.3186f, 0f)
                lineTo(15.696f, 0.1094f)
                arcToRelative(0.2189f, 0.2189f, 0f, false, true, 0.3734f, 0.2189f)
                lineToRelative(-1.0302f, 1.79f)
                curveToRelative(1.6671f, 0.9125f, 2.7974f, 2.5439f, 3.0975f, 4.4018f)
                lineToRelative(-12.286f, -0.0014f)
                curveToRelative(0.3004f, -1.8572f, 1.4305f, -3.488f, 3.0972f, -4.4003f)
                close()
                moveToRelative(5.3774f, 2.6202f)
                arcToRelative(0.515f, 0.515f, 0f, false, false, 0.5271f, 0.5028f)
                arcToRelative(0.515f, 0.515f, 0f, false, false, 0.5151f, -0.5151f)
                arcToRelative(0.5213f, 0.5213f, 0f, false, false, -0.8885f, -0.367f)
                arcToRelative(0.5151f, 0.5151f, 0f, false, false, -0.1537f, 0.3793f)
                close()
                moveToRelative(-5.7178f, -0.0067f)
                arcToRelative(0.5151f, 0.5151f, 0f, false, false, 0.5207f, 0.5095f)
                arcToRelative(0.5086f, 0.5086f, 0f, false, false, 0.367f, -0.1481f)
                arcToRelative(0.5215f, 0.5215f, 0f, true, false, -0.734f, -0.7341f)
                arcToRelative(0.515f, 0.515f, 0f, false, false, -0.1537f, 0.3727f)
                close()
            }
        }.build().also { _androidStudio = it }
    }

@Preview2
@Composable
private fun AndroidStudioPreview() {
    PreviewWrapper {
        Icon(
            imageVector = SdmIcons.AndroidStudio,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _androidStudio: ImageVector? = null
