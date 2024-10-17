package eu.darken.sdmse.common.coil

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import javax.inject.Inject

class BitmapFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coilTempFiles: CoilTempFiles,
    private val gatewaySwitch: GatewaySwitch,
    private val mimeTypeTool: MimeTypeTool,
    private val data: Request,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val target = data.lookup
        if (target.fileType != FileType.FILE) throw IllegalArgumentException("Not a file: $data")
        if (target.size == 0L) throw IllegalArgumentException("Empty file: $data")

        val mimeType = mimeTypeTool.determineMimeType(data.lookup)

        val isValid = mimeType.startsWith("image")
        if (!isValid) throw UnsupportedOperationException("Unsupported mimetype: $mimeType")

        val handle = gatewaySwitch.file(target.lookedUp, readWrite = false)

        return SourceResult(
            ImageSource(buffer, context),
            mimeType,
            dataSource = DataSource.DISK
        )
    }

    class Factory @Inject constructor(
        @ApplicationContext private val context: Context,
        private val coilTempFiles: CoilTempFiles,
        private val gatewaySwitch: GatewaySwitch,
        private val mimeTypeTool: MimeTypeTool,
    ) : Fetcher.Factory<Request> {

        override fun create(
            data: Request,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = BitmapFetcher(context, coilTempFiles, gatewaySwitch, mimeTypeTool, data, options)
    }

    data class Request(
        val lookup: APathLookup<*>
    )
}

