package eu.darken.sdmse.common.coil

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.getIcon2
import javax.inject.Inject

class AppIconFetcher @Inject constructor(
    private val packageManager: PackageManager,
    private val data: Pkg,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        log { "Fetching $data" }
        val baseIcon = packageManager.getIcon2(data.id)
            ?: ContextCompat.getDrawable(options.context, R.drawable.ic_apps)!!

        return DrawableResult(
            drawable = baseIcon,
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    class Factory @Inject constructor(
        private val packageManager: PackageManager,
    ) : Fetcher.Factory<Pkg> {

        override fun create(
            data: Pkg,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = AppIconFetcher(packageManager, data, options)
    }
}

