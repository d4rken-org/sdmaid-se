package eu.darken.sdmse.common.coil

import android.view.View
import androidx.core.view.isInvisible
import coil.request.ImageRequest

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

