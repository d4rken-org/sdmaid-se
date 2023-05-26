package eu.darken.sdmse.common.coil

import android.view.View
import android.widget.ImageView
import androidx.core.view.isInvisible
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.iconRes
import eu.darken.sdmse.common.pkgs.Pkg

fun ImageRequest.Builder.loadingView(
    imageView: View,
    loadingView: View
) {
    listener(
        onStart = {
            loadingView.isInvisible = false
            imageView.isInvisible = true
        },
        onSuccess = { _, _ ->
            loadingView.isInvisible = true
            imageView.isInvisible = false
        }
    )
}

fun ImageView.loadAppIcon(pkg: Pkg): Disposable? {
    val current = tag as? Pkg
    if (current?.packageName == pkg.packageName) return null
    tag = pkg

    val request = ImageRequest.Builder(context).apply {
        data(pkg)
        target(this@loadAppIcon)
    }.build()

    return context.imageLoader.enqueue(request)
}

fun ImageView.loadFilePreview(lookup: APathLookup<*>): Disposable? {
    val current = tag as? APathLookup<*>
    if (current?.lookedUp == lookup.lookedUp) return null
    tag = lookup

    val request = ImageRequest.Builder(context).apply {
        data(lookup)
        val alt = lookup.fileType.iconRes
        fallback(alt)
        error(alt)
        target(this@loadFilePreview)
    }.build()

    return context.imageLoader.enqueue(request)
}