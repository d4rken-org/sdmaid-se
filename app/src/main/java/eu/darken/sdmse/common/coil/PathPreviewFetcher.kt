package eu.darken.sdmse.common.coil

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.decode.DataSource
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
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.files.extension
import eu.darken.sdmse.common.files.iconRes
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject

class PathPreviewFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coilTempFiles: CoilTempFiles,
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

    private val pacMan: PackageManager
        get() = context.packageManager

    override suspend fun fetch(): FetchResult {
        if (data.fileType != FileType.FILE || data.size == 0L) return fallbackIcon

        if (!generalSettings.usePreviews.value()) return fallbackIcon

        val mimeType = mimeTypeTool.determineMimeType(data)

        return when {
            mimeType.startsWith("image") || mimeType.startsWith("video") -> {
                val handle = gatewaySwitch.file(data.lookedUp, readWrite = false)

                SourceResult(
                    handle.toImageSource(coilTempFiles.getBaseCachePath()),
                    mimeType,
                    dataSource = DataSource.DISK
                )
            }

            mimeType == "application/octet-stream" && data.lookedUp.extension == "apk" && data.lookedUp is LocalPath -> {
                val file = data.lookedUp.asFile()

                val iconDrawable = file
                    .takeIf { it.canRead() }
                    ?.let { pacMan.getPackageArchiveInfo(it.path, PackageManager.GET_META_DATA) }
                    ?.let {
                        (it.applicationInfo ?: ApplicationInfo()).apply {
                            sourceDir = file.path
                            publicSourceDir = file.path
                        }
                    }
                    ?.let { pacMan.getApplicationIcon(it) }

                iconDrawable?.let {
                    DrawableResult(
                        drawable = it,
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                } ?: fallbackIcon
            }

            else -> fallbackIcon
        }
    }

    class Factory @Inject constructor(
        @ApplicationContext private val context: Context,
        private val coilTempFiles: CoilTempFiles,
        private val generalSettings: GeneralSettings,
        private val gatewaySwitch: GatewaySwitch,
        private val mimeTypeTool: MimeTypeTool,
    ) : Fetcher.Factory<APathLookup<*>> {

        override fun create(
            data: APathLookup<*>,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = PathPreviewFetcher(
            context,
            coilTempFiles,
            generalSettings,
            gatewaySwitch,
            mimeTypeTool,
            data,
            options,
        )
    }
}

