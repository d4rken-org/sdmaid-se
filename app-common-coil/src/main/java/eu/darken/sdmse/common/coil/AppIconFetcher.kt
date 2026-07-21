package eu.darken.sdmse.common.coil

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.getIcon2
import javax.inject.Inject

class AppIconFetcher @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val data: Pkg,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        log(VERBOSE) { "Fetching $data" }
        val baseIcon = ipcFunnel.use {
            data.icon?.invoke(options.context) ?: packageManager.getIcon2(data.id)
        } ?: ContextCompat.getDrawable(options.context, eu.darken.sdmse.common.io.R.drawable.ic_default_app_icon_24)!!

        // Coil 2 only writes BitmapDrawable fetch results to its memory cache. Rasterizing here also
        // moves adaptive/vector icon drawing off the UI thread and makes subsequent requests cheap.
        val (width, height) = options.resolveAppIconSize()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            density = options.context.resources.displayMetrics.densityDpi
        }
        val previousBounds = Rect(baseIcon.bounds)
        try {
            baseIcon.setBounds(0, 0, width, height)
            baseIcon.draw(Canvas(bitmap))
        } finally {
            baseIcon.bounds = previousBounds
        }

        return DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = false,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory @Inject constructor(
        private val ipcFunnel: IPCFunnel,
    ) : Fetcher.Factory<Pkg> {

        override fun create(
            data: Pkg,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = AppIconFetcher(ipcFunnel, data, options)
    }
}
