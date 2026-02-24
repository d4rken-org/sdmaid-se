package eu.darken.sdmse.common.coil

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.Dimension
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.files.extension
import eu.darken.sdmse.common.files.iconRes
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.main.core.GeneralSettings
import okio.buffer
import java.io.IOException
import javax.inject.Inject

class PathPreviewFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coilTempFiles: CoilTempFiles,
    private val generalSettings: GeneralSettings,
    private val gatewaySwitch: GatewaySwitch,
    private val mimeTypeTool: MimeTypeTool,
    private val textPreviewRenderer: TextPreviewRenderer,
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

            data.lookedUp.extension == "apk" && data.lookedUp is LocalPath -> {
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

            isTextFile(data) -> {
                renderTextPreview() ?: fallbackIcon
            }

            else -> fallbackIcon
        }
    }

    private suspend fun renderTextPreview(): DrawableResult? {
        return try {
            val (width, height) = resolveSize()

            val bytes = gatewaySwitch.file(data.lookedUp, readWrite = false).use { handle ->
                val readSize = minOf(handle.size(), MAX_TEXT_READ_BYTES)
                handle.source().buffer().use { source ->
                    source.readByteArray(readSize)
                }
            }

            if (bytes.any { it == 0.toByte() }) return null

            val text = String(bytes, Charsets.UTF_8)
                .replace("\r", "")
                .replace("\t", "    ")

            val density = context.resources.displayMetrics.density
            val bitmap = textPreviewRenderer.render(text, width, height, density)

            DrawableResult(
                drawable = BitmapDrawable(context.resources, bitmap),
                isSampled = false,
                dataSource = DataSource.DISK,
            )
        } catch (e: IOException) {
            log(TAG, WARN) { "Text preview failed for ${data.lookedUp}: ${e.asLog()}" }
            null
        }
    }

    private fun resolveSize(): Pair<Int, Int> {
        val width = when (val w = options.size.width) {
            is Dimension.Pixels -> w.px
            Dimension.Undefined -> DEFAULT_SIZE_PX
        }.coerceIn(1, MAX_SIZE_PX)

        val height = when (val h = options.size.height) {
            is Dimension.Pixels -> h.px
            Dimension.Undefined -> DEFAULT_SIZE_PX
        }.coerceIn(1, MAX_SIZE_PX)

        return width to height
    }

    class Factory @Inject constructor(
        @ApplicationContext private val context: Context,
        private val coilTempFiles: CoilTempFiles,
        private val generalSettings: GeneralSettings,
        private val gatewaySwitch: GatewaySwitch,
        private val mimeTypeTool: MimeTypeTool,
        private val textPreviewRenderer: TextPreviewRenderer,
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
            textPreviewRenderer,
            data,
            options,
        )
    }

    companion object {
        private val TAG = logTag("Coil", "PathPreview")
        private const val MAX_TEXT_READ_BYTES = 8192L
        private const val DEFAULT_SIZE_PX = 256
        private const val MAX_SIZE_PX = 512

        private val TEXT_EXTENSIONS = setOf(
            "txt", "text", "log",
            "html", "htm", "xhtml",
            "xml", "json", "yaml", "yml",
            "csv", "tsv",
            "md", "markdown",
            "cfg", "conf", "ini", "properties",
            "sh", "bash",
            "java", "kt", "kts", "py", "js", "ts",
            "css", "scss",
            "sql", "gradle",
        )

        fun isTextFile(data: APathLookup<*>): Boolean {
            return data.lookedUp.extension?.lowercase() in TEXT_EXTENSIONS
        }
    }
}

