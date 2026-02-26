package eu.darken.sdmse.common.coil

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.IOException
import androidx.core.graphics.createBitmap

internal fun renderPdfFirstPage(pfd: ParcelFileDescriptor, maxDimension: Int = 512): Bitmap? = try {
    PdfRenderer(pfd).use { renderer ->
        if (renderer.pageCount == 0) return@use null

        renderer.openPage(0).use { page ->
            if (page.width <= 0 || page.height <= 0) return@use null

            val (width, height) = scaleToFit(page.width, page.height, maxDimension)
            val bitmap = createBitmap(width, height)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }
} catch (_: SecurityException) {
    null
} catch (_: IOException) {
    null
}

private fun scaleToFit(srcWidth: Int, srcHeight: Int, maxDim: Int): Pair<Int, Int> {
    if (srcWidth <= maxDim && srcHeight <= maxDim) return srcWidth to srcHeight
    val scale = maxDim.toFloat() / maxOf(srcWidth, srcHeight)
    return (srcWidth * scale).toInt().coerceAtLeast(1) to (srcHeight * scale).toInt().coerceAtLeast(1)
}
