package eu.darken.sdmse.common.coil

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.R as CommonR

/**
 * Compose counterpart to the legacy `ImageView.loadFilePreview()` helper.
 *
 * Loads a preview for [lookup] via Coil with a file-type fallback / error icon, so rows behave
 * identically when decoding fails or the file has no preview.
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
    val fallback = when (lookup.fileType) {
        FileType.DIRECTORY -> CommonR.drawable.ic_folder
        FileType.SYMBOLIC_LINK -> CommonR.drawable.ic_file_link
        FileType.FILE -> CommonR.drawable.ic_file
        FileType.UNKNOWN -> CommonR.drawable.file_question
    }
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
