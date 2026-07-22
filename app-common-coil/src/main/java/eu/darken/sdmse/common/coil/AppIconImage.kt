package eu.darken.sdmse.common.coil

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.io.R as IoR

/**
 * Launcher-icon for a [Pkg], loaded via Coil's [AppIconFetcher] at runtime.
 *
 * Under `@Preview`/screenshot rendering (`LocalInspectionMode`) Coil can't resolve a package's icon on
 * the render host, so this renders a sample from [LocalPreviewImageProvider] when one is installed
 * (screenshot tests), or the default app-icon vector otherwise (IDE previews) — never letting Coil run
 * under layoutlib, where it would blank out.
 */
@Composable
fun AppIconImage(
    pkg: Pkg,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (LocalInspectionMode.current) {
        val sample = LocalPreviewImageProvider.current?.appIcon(pkg)
        if (sample != null) {
            Image(
                painter = sample,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                painter = painterResource(IoR.drawable.ic_default_app_icon_24),
                contentDescription = contentDescription,
                modifier = modifier,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val context = LocalContext.current
    AsyncImage(
        model = remember(pkg) { ImageRequest.Builder(context).data(pkg).build() },
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
