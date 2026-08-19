package eu.darken.sdmse.widget.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.size
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.ui.R as CommonUiR
import kotlin.math.roundToInt

/**
 * A determinate storage "donut" that fills to the used ratio, with the percentage in the centre.
 *
 * Glance has no determinate circular progress, so we draw a [Bitmap] at runtime and hand it to a
 * Glance [Image]. The arc colour comes from [storageArcColor]; the track and label follow light/dark.
 *
 * [isLow] means free space is at or below the configured low-storage threshold — the ring is the
 * only storage indicator in the narrow single-row layout, so it has to carry that signal itself.
 */
@Composable
internal fun StorageRing(ratio: Float, diameter: Dp, isLow: Boolean = false) {
    val context = LocalContext.current
    val px = (diameter.value * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    Image(
        provider = ImageProvider(storageRingBitmap(context, ratio, px, isLow)),
        contentDescription = null,
        modifier = GlanceModifier.size(diameter),
    )
}

/**
 * Arc colour for the ring. Extracted and pure so it can be unit-tested — the bitmap itself can't be
 * (Robolectric's Canvas doesn't rasterise).
 *
 * The low-storage branch deliberately comes FIRST: a Material You wallpaper accent must never
 * override the warning colour, otherwise a device with an amber-ish accent shows no difference at all.
 */
@ColorInt
internal fun storageArcColor(context: Context, isLow: Boolean): Int = when {
    isLow -> context.getColor(CommonUiR.color.md_theme_storageLow)
    hasApiLevel(31) -> context.getColor(android.R.color.system_accent1_500)
    else -> context.getColor(CommonUiR.color.md_theme_primary)
}

private fun storageRingBitmap(context: Context, ratio: Float, sizePx: Int, isLow: Boolean): Bitmap {
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = sizePx * 0.13f
    // Inset by exactly half the stroke so the ring's OUTER edge is flush with the bitmap bounds —
    // giving it the same rendered diameter as the mascot icon and Clean circle beside it (all
    // NARROW_ELEMENT_SIZE). Any extra padding here makes the ring look smaller than its neighbours.
    val pad = stroke / 2f
    val bounds = RectF(pad, pad, sizePx - pad, sizePx - pad)

    val arcColor = storageArcColor(context, isLow)
    val trackColor = if (night) 0x33FFFFFF else 0x1F000000
    val textColor = if (night) 0xFFECECEC.toInt() else 0xFF1B1B1B.toInt()

    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    ring.color = trackColor
    canvas.drawArc(bounds, 0f, 360f, false, ring)
    ring.color = arcColor
    canvas.drawArc(bounds, -90f, 360f * ratio.coerceIn(0f, 1f), false, ring)

    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.30f
        typeface = Typeface.DEFAULT_BOLD
    }
    val label = "${(ratio.coerceIn(0f, 1f) * 100).roundToInt()}%"
    val fm = text.fontMetrics
    canvas.drawText(label, sizePx / 2f, sizePx / 2f - (fm.ascent + fm.descent) / 2f, text)

    return bitmap
}
