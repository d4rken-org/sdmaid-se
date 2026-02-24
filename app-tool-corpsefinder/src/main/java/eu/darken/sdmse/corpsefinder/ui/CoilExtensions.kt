package eu.darken.sdmse.corpsefinder.ui

import android.widget.ImageView
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.corpsefinder.R

private val FileType.iconRes: Int
    get() = when (this) {
        FileType.DIRECTORY -> R.drawable.ic_folder
        FileType.SYMBOLIC_LINK -> R.drawable.ic_file
        FileType.FILE -> R.drawable.ic_file
        FileType.UNKNOWN -> R.drawable.file_question
    }

fun ImageView.loadFilePreview(
    lookup: APathLookup<*>,
): Disposable? {
    val current = tag as? APathLookup<*>
    if (current?.lookedUp == lookup.lookedUp) return null
    tag = lookup

    val alt = lookup.fileType.iconRes
    val request = ImageRequest.Builder(context).apply {
        data(lookup)
        target(this@loadFilePreview)
        fallback(alt)
        error(alt)
    }.build()

    return context.imageLoader.enqueue(request)
}
