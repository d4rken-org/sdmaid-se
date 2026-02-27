package eu.darken.sdmse.common.coil

import android.widget.ImageView
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import eu.darken.sdmse.common.pkgs.Pkg

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
