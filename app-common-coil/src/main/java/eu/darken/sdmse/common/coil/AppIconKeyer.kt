package eu.darken.sdmse.common.coil

import android.content.res.Configuration
import coil.key.Keyer
import coil.request.Options
import coil.size.Dimension
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.PkgInfo

class AppIconKeyer : Keyer<Pkg> {

    override fun key(data: Pkg, options: Options): String {
        val configuration = options.context.resources.configuration
        val (width, height) = options.resolveAppIconSize()
        val pkgInfo = data as? PkgInfo

        return buildString {
            append("sdmse:app-icon:v1")
            append("|type=").append(data.javaClass.name)
            append("|package=").append(data.id.name)
            append("|user=").append((data as? Installed)?.userHandle?.handleId ?: NO_USER)
            append("|version=").append(pkgInfo?.versionCode ?: NO_VERSION)
            append("|updated=").append(pkgInfo?.packageInfo?.lastUpdateTime ?: NO_UPDATE_TIME)
            append("|icon=").append(pkgInfo?.applicationInfo?.icon ?: NO_ICON_RESOURCE)
            append("|source=").append(pkgInfo?.applicationInfo?.sourceDir.orEmpty())
            append("|density=").append(options.context.resources.displayMetrics.densityDpi)
            append("|night=").append(configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
            append("|size=").append(width).append('x').append(height)
        }
    }

    private companion object {
        const val NO_USER = "none"
        const val NO_VERSION = "unknown"
        const val NO_UPDATE_TIME = "unknown"
        const val NO_ICON_RESOURCE = "unknown"
    }
}

internal fun Options.resolveAppIconSize(): Pair<Int, Int> {
    val requestedWidth = (size.width as? Dimension.Pixels)?.px
    val requestedHeight = (size.height as? Dimension.Pixels)?.px
    val fallbackSize = (DEFAULT_SIZE_DP * context.resources.displayMetrics.density)
        .toInt()
        .coerceAtLeast(1)

    val inferredSize = requestedWidth ?: requestedHeight ?: fallbackSize
    return (requestedWidth ?: inferredSize).coerceIn(1, MAX_SIZE_PX) to
        (requestedHeight ?: inferredSize).coerceIn(1, MAX_SIZE_PX)
}

private const val DEFAULT_SIZE_DP = 48
private const val MAX_SIZE_PX = 512
