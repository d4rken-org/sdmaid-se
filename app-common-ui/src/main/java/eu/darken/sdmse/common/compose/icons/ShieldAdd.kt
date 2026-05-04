/*
 * Adapted from Fluent UI System Icons
 * https://github.com/microsoft/fluentui-system-icons
 * MIT License — Copyright (c) 2020 Microsoft Corporation
 */
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

val SdmIcons.ShieldAdd: ImageVector
    get() {
        _shieldAdd?.let { return it }
        return ImageVector.Builder(
            name = "ShieldAdd",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(3f, 5.75f)
                curveTo(3f, 5.33579f, 3.33579f, 5f, 3.75f, 5f)
                curveTo(6.41341f, 5f, 9.00797f, 4.05652f, 11.55f, 2.15f)
                curveTo(11.8167f, 1.95f, 12.1833f, 1.95f, 12.45f, 2.15f)
                curveTo(14.992f, 4.05652f, 17.5866f, 5f, 20.25f, 5f)
                curveTo(20.6642f, 5f, 21f, 5.33579f, 21f, 5.75f)
                verticalLineTo(11f)
                curveTo(21f, 11.3381f, 20.9865f, 11.6701f, 20.9595f, 11.9961f)
                curveTo(19.9577f, 11.3651f, 18.7715f, 11f, 17.5f, 11f)
                curveTo(13.9101f, 11f, 11f, 13.9101f, 11f, 17.5f)
                curveTo(11f, 19.151f, 11.6156f, 20.6583f, 12.6297f, 21.8048f)
                curveTo(12.5126f, 21.8531f, 12.3944f, 21.9007f, 12.2749f, 21.9478f)
                curveTo(12.0982f, 22.0174f, 11.9018f, 22.0174f, 11.7251f, 21.9478f)
                curveTo(5.95756f, 19.6757f, 3f, 16.0012f, 3f, 11f)
                verticalLineTo(5.75f)
                close()
                moveTo(23f, 17.5f)
                curveTo(23f, 14.4624f, 20.5376f, 12f, 17.5f, 12f)
                curveTo(14.4624f, 12f, 12f, 14.4624f, 12f, 17.5f)
                curveTo(12f, 20.5376f, 14.4624f, 23f, 17.5f, 23f)
                curveTo(20.5376f, 23f, 23f, 20.5376f, 23f, 17.5f)
                close()
                moveTo(18.0006f, 18f)
                lineTo(18.0011f, 20.5035f)
                curveTo(18.0011f, 20.7797f, 17.7773f, 21.0035f, 17.5011f, 21.0035f)
                curveTo(17.225f, 21.0035f, 17.0011f, 20.7797f, 17.0011f, 20.5035f)
                lineTo(17.0006f, 18f)
                horizontalLineTo(14.4956f)
                curveTo(14.2197f, 18f, 13.9961f, 17.7762f, 13.9961f, 17.5f)
                curveTo(13.9961f, 17.2239f, 14.2197f, 17f, 14.4956f, 17f)
                horizontalLineTo(17.0005f)
                lineTo(17f, 14.4993f)
                curveTo(17f, 14.2231f, 17.2239f, 13.9993f, 17.5f, 13.9993f)
                curveTo(17.7761f, 13.9993f, 18f, 14.2231f, 18f, 14.4993f)
                lineTo(18.0005f, 17f)
                horizontalLineTo(20.4966f)
                curveTo(20.7725f, 17f, 20.9961f, 17.2239f, 20.9961f, 17.5f)
                curveTo(20.9961f, 17.7762f, 20.7725f, 18f, 20.4966f, 18f)
                horizontalLineTo(18.0006f)
                close()
            }
        }.build().also { _shieldAdd = it }
    }

@Preview2
@Composable
private fun ShieldAddPreview() {
    PreviewWrapper {
        Icon(
            imageVector = SdmIcons.ShieldAdd,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _shieldAdd: ImageVector? = null
