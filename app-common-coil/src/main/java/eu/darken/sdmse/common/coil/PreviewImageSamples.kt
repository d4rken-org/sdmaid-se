package eu.darken.sdmse.common.coil

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.pkgs.Pkg

/**
 * A [PreviewImageProvider] for screenshot/preview renders where Coil can't run (layoutlib).
 *
 * Both file thumbnails ([filePainters]) and app icons ([iconPainters]) use the supplied painters when
 * given (deterministically picked per file path / package name so the same item looks the same across
 * locales), otherwise a generated gradient tile. Pass real sample images for screens where the content
 * matters via `rememberSampleImageProvider(filePainters = …, iconPainters = …)` with
 * `painterResource(R.drawable.…)` — the drawables live in that module's `debug/res` (a `screenshotTest`
 * source set can read `debug` res via its R class, but not its own `res`). Icon samples are neutral
 * generated glyphs, never real launcher icons (those are third-party trademarks).
 *
 * Production never installs a provider, so this only ever runs under inspection/screenshot rendering.
 */
@Composable
fun rememberSampleImageProvider(
    filePainters: List<Painter> = emptyList(),
    iconPainters: List<Painter> = emptyList(),
): PreviewImageProvider = SampleImageProvider(filePainters, iconPainters)

private class SampleImageProvider(
    private val filePainters: List<Painter>,
    private val iconPainters: List<Painter>,
) : PreviewImageProvider {
    @Composable
    override fun fileImage(lookup: APathLookup<*>): Painter = when {
        filePainters.isEmpty() -> GradientSamplePainter(lookup.path.hashCode())
        else -> filePainters[Math.floorMod(lookup.path.hashCode(), filePainters.size)]
    }

    @Composable
    override fun appIcon(pkg: Pkg): Painter = when {
        iconPainters.isEmpty() -> GradientSamplePainter(pkg.packageName.hashCode())
        else -> iconPainters[Math.floorMod(pkg.packageName.hashCode(), iconPainters.size)]
    }
}

private class GradientSamplePainter(seed: Int) : Painter() {
    private val top = sampleColor(seed)
    private val bottom = sampleColor(seed * 31 + 17)

    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(top, bottom),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
    }
}

private fun sampleColor(seed: Int): Color {
    // floorMod (not abs) so Int.MIN_VALUE can't yield a negative hue.
    val hue = Math.floorMod(seed, 360).toFloat()
    return Color.hsv(hue, 0.45f, 0.80f)
}
