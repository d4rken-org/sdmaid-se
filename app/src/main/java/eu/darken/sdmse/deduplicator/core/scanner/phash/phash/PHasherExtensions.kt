package eu.darken.sdmse.deduplicator.core.scanner.phash.phash

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import eu.darken.sdmse.common.files.APathLookup
import java.io.IOException

suspend fun APathLookup<*>.phash(context: Context): PHasher.Result {
    val request = ImageRequest.Builder(context).apply {
        data(this@phash)
        // Hardware backed bitmaps don't support direct pixel access
        allowHardware(false)
    }.build()

    val result = context.imageLoader.execute(request)

    if (result !is SuccessResult) {
        throw IOException("Failed to load bitmap for $this: $result")
    }

    val hasher = PHasher()
    return hasher.calc(result.drawable.toBitmap())
}