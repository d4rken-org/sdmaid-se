package eu.darken.sdmse.common.coil

import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale

object ZoomImageRequests {
    // MAX * MAX px at 8 B/px (RGBA_F16, Coil 2.7.0) stays below RecordingCanvas' 100 MiB bitmap limit.
    const val MAX = 3584
}

fun ImageRequest.Builder.capZoomDecodeSize(): ImageRequest.Builder = this
    .size(ZoomImageRequests.MAX, ZoomImageRequests.MAX)
    .precision(Precision.INEXACT)
    .scale(Scale.FIT)
