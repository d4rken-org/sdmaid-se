package eu.darken.sdmse.common.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SdmIcons.Bug: ImageVector
    get() {
        _bug?.let { return it }
        return ImageVector.Builder(
            name = "Bug",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(511.988f, 288.9f)
                curveToRelative(-0.478f, 17.43f, -15.217f, 31.1f, -32.653f, 31.1f)
                horizontalLineTo(424f)
                verticalLineToRelative(16f)
                curveToRelative(0f, 21.864f, -4.882f, 42.584f, -13.6f, 61.145f)
                lineToRelative(60.228f, 60.228f)
                curveToRelative(12.496f, 12.497f, 12.496f, 32.758f, 0f, 45.255f)
                curveToRelative(-12.498f, 12.497f, -32.759f, 12.496f, -45.256f, 0f)
                lineToRelative(-54.736f, -54.736f)
                curveTo(345.886f, 467.965f, 314.351f, 480f, 280f, 480f)
                verticalLineTo(236f)
                curveToRelative(0f, -6.627f, -5.373f, -12f, -12f, -12f)
                horizontalLineToRelative(-24f)
                curveToRelative(-6.627f, 0f, -12f, 5.373f, -12f, 12f)
                verticalLineToRelative(244f)
                curveToRelative(-34.351f, 0f, -65.886f, -12.035f, -90.636f, -32.108f)
                lineToRelative(-54.736f, 54.736f)
                curveToRelative(-12.498f, 12.497f, -32.759f, 12.496f, -45.256f, 0f)
                curveToRelative(-12.496f, -12.497f, -12.496f, -32.758f, 0f, -45.255f)
                lineToRelative(60.228f, -60.228f)
                curveTo(92.882f, 378.584f, 88f, 357.864f, 88f, 336f)
                verticalLineToRelative(-16f)
                horizontalLineTo(32.666f)
                curveTo(15.23f, 320f, 0.491f, 306.33f, 0.013f, 288.9f)
                curveTo(-0.484f, 270.816f, 14.028f, 256f, 32f, 256f)
                horizontalLineToRelative(56f)
                verticalLineToRelative(-58.745f)
                lineToRelative(-46.628f, -46.628f)
                curveToRelative(-12.496f, -12.497f, -12.496f, -32.758f, 0f, -45.255f)
                curveToRelative(12.498f, -12.497f, 32.758f, -12.497f, 45.256f, 0f)
                lineTo(141.255f, 160f)
                horizontalLineToRelative(229.489f)
                lineToRelative(54.627f, -54.627f)
                curveToRelative(12.498f, -12.497f, 32.758f, -12.497f, 45.256f, 0f)
                curveToRelative(12.496f, 12.497f, 12.496f, 32.758f, 0f, 45.255f)
                lineTo(424f, 197.255f)
                verticalLineTo(256f)
                horizontalLineToRelative(56f)
                curveToRelative(17.972f, 0f, 32.484f, 14.816f, 31.988f, 32.9f)
                close()
                moveTo(257f, 0f)
                curveToRelative(-61.856f, 0f, -112f, 50.144f, -112f, 112f)
                horizontalLineToRelative(224f)
                curveTo(369f, 50.144f, 318.856f, 0f, 257f, 0f)
                close()
            }
        }.build().also { _bug = it }
    }
private var _bug: ImageVector? = null
