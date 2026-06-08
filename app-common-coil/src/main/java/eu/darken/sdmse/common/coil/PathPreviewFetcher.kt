package eu.darken.sdmse.common.coil

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
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
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.common.R as CommonR
import okio.buffer
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject

class PathPreviewFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coilTempFiles: CoilTempFiles,
    private val generalSettings: GeneralSettings,
    private val gatewaySwitch: GatewaySwitch,
    private val mimeTypeTool: MimeTypeTool,
    private val textPreviewRenderer: TextPreviewRenderer,
    private val storageManager: StorageManager,
    private val data: APathLookup<*>,
    private val options: Options,
) : Fetcher {

    private val fallbackIcon by lazy {
        val fallbackResId = when (data.fileType) {
            FileType.DIRECTORY -> CommonR.drawable.ic_folder
            FileType.SYMBOLIC_LINK -> CommonR.drawable.ic_file_link
            FileType.FILE -> CommonR.drawable.ic_file
            FileType.UNKNOWN -> CommonR.drawable.file_question
        }
        DrawableResult(
            drawable = ContextCompat.getDrawable(options.context, fallbackResId)!!,
            isSampled = false,
            dataSource = DataSource.MEMORY
        )
    }

    private val pacMan: PackageManager
        get() = context.packageManager

    /**
     * The drawable fallback shown when no real preview exists. When a caller sets
     * [PARAM_NO_DRAWABLE_FALLBACK] (Compose thumbnails that own their own fallback visual), throw
     * instead so Coil routes to the request's error slot rather than rendering [fallbackIcon].
     */
    private fun fallbackResult(): FetchResult {
        if (options.parameters.value<Boolean>(PARAM_NO_DRAWABLE_FALLBACK) == true) {
            throw PreviewUnavailableException()
        }
        return fallbackIcon
    }

    override suspend fun fetch(): FetchResult {
        if (data.fileType != FileType.FILE || data.size == 0L) return fallbackResult()

        if (!generalSettings.usePreviews.value()) return fallbackResult()

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
                } ?: fallbackResult()
            }

            mimeType == "application/pdf" -> {
                if (data.size > PDF_MAX_PREVIEW_SIZE) fallbackResult()
                else renderPdfPreview() ?: fallbackResult()
            }

            isTextFile(data) -> {
                renderTextPreview() ?: fallbackResult()
            }

            else -> fallbackResult()
        }
    }

    private suspend fun renderPdfPreview(): DrawableResult? = try {
        val handle = gatewaySwitch.file(data.lookedUp, readWrite = false)
        val pfd = handle.toProxyPfd(storageManager)
        // PdfRenderer takes ownership of the PFD and closes it
        val bitmap = renderPdfFirstPage(pfd) ?: return null

        DrawableResult(
            drawable = bitmap.toDrawable(context.resources),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "PDF preview failed for ${data.lookedUp}: ${e.asLog()}" }
        null
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
        private val storageManager: StorageManager,
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
            storageManager,
            data,
            options,
        )
    }

    companion object {
        private val TAG = logTag("Coil", "PathPreview")

        /**
         * Coil request parameter (Boolean). When `true`, the fetcher throws instead of returning the
         * [fallbackIcon] drawable, so the caller's error slot can render its own placeholder. Set by
         * Compose thumbnails (e.g. [FileListThumbnail]); unset for the legacy `PreviewScreen` path.
         */
        const val PARAM_NO_DRAWABLE_FALLBACK = "sdmse.coil.no_drawable_fallback"

        private const val MAX_TEXT_READ_BYTES = 8192L
        private const val DEFAULT_SIZE_PX = 256
        private const val MAX_SIZE_PX = 512
        private const val PDF_MAX_PREVIEW_SIZE = 50L * 1024 * 1024

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

/**
 * Control-flow signal that no preview drawable should be produced (see
 * [PathPreviewFetcher.PARAM_NO_DRAWABLE_FALLBACK]). Coil turns the throw into an error result so the
 * request's error slot renders. Stack trace capture is suppressed since it carries no useful info.
 */
class PreviewUnavailableException : Exception() {
    override fun fillInStackTrace(): Throwable = this
}

