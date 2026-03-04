package eu.darken.sdmse.common.coil

import android.widget.ImageView
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.iconRes

fun ImageView.loadFilePreview(
    lookup: APathLookup<*>,
    options: ImageRequest.Builder.(APathLookup<*>) -> Unit = {
        val alt = lookup.fileType.iconRes
        fallback(alt)
        error(alt)
    },
): Disposable? {
    val current = tag as? APathLookup<*>
    if (current?.lookedUp == lookup.lookedUp) return null
    tag = lookup

    val request = ImageRequest.Builder(context).apply {
        data(lookup)
        target(this@loadFilePreview)
        options(lookup)
    }.build()

    return context.imageLoader.enqueue(request)
}
