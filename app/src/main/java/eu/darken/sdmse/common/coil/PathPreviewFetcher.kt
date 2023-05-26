package eu.darken.sdmse.common.coil

import android.content.Context
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.iconRes
import eu.darken.sdmse.main.core.GeneralSettings
import okio.buffer
import javax.inject.Inject

class PathPreviewFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
    private val gatewaySwitch: GatewaySwitch,
    private val mimeTypeTool: MimeTypeTool,
    private val data: APathLookup<*>,
    private val options: Options,
) : Fetcher {

    private val fallbackIcon by lazy {
        DrawableResult(
            drawable = ContextCompat.getDrawable(options.context, data.fileType.iconRes)!!,
            isSampled = false,
            dataSource = DataSource.MEMORY
        )
    }

    override suspend fun fetch(): FetchResult {
        if (data.fileType != FileType.FILE || data.size == 0L) return fallbackIcon

        if (!generalSettings.usePreviews.value()) return fallbackIcon

        val mimeType = mimeTypeTool.determineMimeType(data)

        val isValid = mimeType.startsWith("image") || mimeType.startsWith("video")
        if (!isValid) return fallbackIcon

        val buffer = gatewaySwitch.read(data.lookedUp).buffer()

        return SourceResult(
            ImageSource(buffer, context),
            mimeType,
            dataSource = DataSource.DISK
        )
    }

    class Factory @Inject constructor(
        @ApplicationContext private val context: Context,
        private val generalSettings: GeneralSettings,
        private val gatewaySwitch: GatewaySwitch,
        private val mimeTypeTool: MimeTypeTool,
    ) : Fetcher.Factory<APathLookup<*>> {

        override fun create(
            data: APathLookup<*>,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = PathPreviewFetcher(context, generalSettings, gatewaySwitch, mimeTypeTool, data, options)
    }
}

