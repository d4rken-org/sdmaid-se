package eu.darken.sdmse.common.coil

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.TextPaint
import dagger.Reusable
import javax.inject.Inject
import androidx.core.graphics.createBitmap

@Reusable
class TextPreviewRenderer @Inject constructor() {

    fun render(
        text: String,
        width: Int,
        height: Int,
        density: Float,
    ): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        canvas.drawColor(BACKGROUND_COLOR)

        val padding = (PADDING_DP * density)
        val textSize = (height / 20f).coerceIn(MIN_TEXT_SIZE_PX, MAX_TEXT_SIZE_PX)

        val paint = TextPaint().apply {
            isAntiAlias = true
            color = TEXT_COLOR
            typeface = Typeface.MONOSPACE
            this.textSize = textSize
        }

        val lineHeight = paint.fontMetrics.let { it.descent - it.ascent + it.leading }
        val maxWidth = width - 2 * padding
        var y = padding - paint.fontMetrics.ascent
        val lines = text.lines()

        for (line in lines) {
            if (y + paint.fontMetrics.descent > height - padding) break
            canvas.save()
            canvas.clipRect(padding, 0f, padding + maxWidth, height.toFloat())
            canvas.drawText(line, padding, y, paint)
            canvas.restore()
            y += lineHeight
        }

        return bitmap
    }

    companion object {
        private const val BACKGROUND_COLOR = 0xFFF5F5F5.toInt()
        private const val TEXT_COLOR = 0xFF333333.toInt()
        private const val PADDING_DP = 4f
        private const val MIN_TEXT_SIZE_PX = 6f
        private const val MAX_TEXT_SIZE_PX = 16f
    }
}
