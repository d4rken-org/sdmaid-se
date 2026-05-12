package eu.darken.sdmse.common.coil

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
 *
 * Items that can never have a real preview (directories, symlinks, unknown types, zero-size files)
 * are rendered as a Compose [Icon] with [fallbackTint] applied. This bypasses Coil's drawable
 * pipeline, where the vector's `android:tint="?attr/colorControlNormal"` does not resolve
 * reliably and can leave icons white on a light surface.
 */
@Composable
fun FilePreviewImage(
    lookup: APathLookup<*>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    fallbackTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val fallbackRes = when (lookup.fileType) {
        FileType.DIRECTORY -> CommonR.drawable.ic_folder
        FileType.SYMBOLIC_LINK -> CommonR.drawable.ic_file_link
        FileType.FILE -> CommonR.drawable.ic_file
        FileType.UNKNOWN -> CommonR.drawable.file_question
    }

    val canHavePreview = lookup.fileType == FileType.FILE && lookup.size > 0L
    if (!canHavePreview) {
        Icon(
            painter = painterResource(fallbackRes),
            contentDescription = contentDescription,
            modifier = modifier,
            tint = fallbackTint,
        )
        return
    }

    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(lookup)
        .fallback(fallbackRes)
        .error(fallbackRes)
        .build()

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
    )
}
