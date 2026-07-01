package eu.darken.sdmse.widget.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.size
import eu.darken.sdmse.common.ui.R as CommonUiR
import kotlin.math.roundToInt

/**
 * A determinate storage "donut" that fills to the used ratio, with the percentage in the centre.
 *
 * Glance has no determinate circular progress, so we draw a [Bitmap] at runtime and hand it to a
 * Glance [Image]. The arc uses the Material You accent on API 31+, else the app's primary; the track
 * and label follow light/dark.
 */
@Composable
internal fun StorageRing(ratio: Float, diameter: Dp) {
    val context = LocalContext.current
    val px = (diameter.value * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    Image(
        provider = ImageProvider(storageRingBitmap(context, ratio, px)),
        contentDescription = null,
        modifier = GlanceModifier.size(diameter),
    )
}

private fun storageRingBitmap(context: Context, ratio: Float, sizePx: Int): Bitmap {
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = sizePx * 0.13f
    val pad = stroke / 2f + sizePx * 0.04f
    val bounds = RectF(pad, pad, sizePx - pad, sizePx - pad)

    val arcColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getColor(android.R.color.system_accent1_500)
    } else {
        context.getColor(CommonUiR.color.md_theme_primary)
    }
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
