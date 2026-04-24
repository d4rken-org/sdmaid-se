package eu.darken.sdmse.common.coil

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.iconRes

/**
 * Compose counterpart to `ImageView.loadFilePreview()`.
 *
 * Loads a preview for [lookup] via Coil with the same file-type fallback / error icon as the
 * legacy XML helper, so rows behave identically when decoding fails or the file has no preview.
 */
@Composable
fun FilePreviewImage(
    lookup: APathLookup<*>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
) {
    val context = LocalContext.current
    val fallback = lookup.fileType.iconRes
    val request = ImageRequest.Builder(context)
        .data(lookup)
        .fallback(fallback)
        .error(fallback)
        .build()

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
    )
}
