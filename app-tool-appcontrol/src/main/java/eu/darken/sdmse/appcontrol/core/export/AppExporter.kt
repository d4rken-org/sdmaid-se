package eu.darken.sdmse.appcontrol.core.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.MimeTypes
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compression.Zipper
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.FileMode
import eu.darken.sdmse.common.files.saf.SAFDocFile
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.SourceAvailable
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressPrimary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.parcelize.Parcelize
import okio.IOException
import okio.buffer
import okio.sink
import okio.source
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@Reusable
class AppExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentResolver: ContentResolver,
    private val mimeTypeTool: MimeTypeTool,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.Data(primary = R.string.general_progress_preparing.toCaString())
    )

    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(50)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun save(target: AppInfo, directoryUri: Uri): Result {
        log(TAG) { "save(target=$target, $directoryUri)" }
        target.pkg as SourceAvailable
        val baseApk = target.pkg.sourceDir
        log(TAG) { "Base APK is $baseApk" }

        val extraSources = target.pkg.splitSources
        log(TAG) { "Split sources are $extraSources" }

        // The declared type has to pair up with the extension, otherwise the framework treats the
        // whole display name as the base name and appends a collision counter behind the extension.
        val (extension, mimeType) = when (target.exportType) {
            AppExportType.APK -> EXTENSION_APK to MimeTypes.Apk.value
            AppExportType.BUNDLE -> EXTENSION_BUNDLE to mimeTypeTool.fromExtension(EXTENSION_BUNDLE)
            AppExportType.NONE -> throw IllegalArgumentException("Can't export $target")
        }

        // Everything that can say "there is nothing to write" has to say it before a document exists,
        // otherwise the failure leaves a zero byte file sitting on the preferred name.
        val sources: Set<APath> = when (target.exportType) {
            AppExportType.APK -> setOf(baseApk ?: throw IllegalStateException("APK file unavailable"))
            AppExportType.BUNDLE -> (setOfNotNull(baseApk) + extraSources.orEmpty()).ifEmpty {
                throw IllegalStateException("BUNDLE is empty")
            }

            AppExportType.NONE -> throw IllegalArgumentException("Can't export $target")
        }
        log(TAG) { "Sources to write are $sources" }

        val baseName = "${target.label.get(context)} (${target.installId.pkgId.name}) - " +
                "${target.pkg.versionName}[${target.pkg.versionCode}]"

        val saveDir = SAFDocFile.fromTreeUri(context, contentResolver, directoryUri)

        val savePath = createExportFile(saveDir, baseName, extension, mimeType)

        try {
            if (!savePath.writable) throw IOException("$savePath is not writable")

            val pfd = savePath
                .openPFD(contentResolver, FileMode.WRITE)
                .let { ParcelFileDescriptor.AutoCloseOutputStream(it) }

            pfd.use { output ->
                when (target.exportType) {
                    AppExportType.APK -> {
                        val apk = sources.single()
                        updateProgressPrimary(apk.userReadablePath)
                        output.sink().buffer().use { sink ->
                            (apk as LocalPath).file.source().buffer().use { source ->
                                sink.writeAll(source)
                            }
                        }
                    }

                    AppExportType.BUNDLE -> { // Create ZIP
                        ZipOutputStream(output.sink().buffer().outputStream()).use { zipOut ->
                            sources.forEach { file ->
                                updateProgressPrimary(file.userReadablePath)
                                val zipEntry = ZipEntry(file.name)
                                zipOut.putNextEntry(zipEntry)
                                BufferedInputStream(FileInputStream(file.asFile()), Zipper.BUFFER).use { input ->
                                    input.copyTo(zipOut)
                                }
                                zipOut.closeEntry()
                            }
                        }
                    }

                    AppExportType.NONE -> throw IllegalStateException("Should never get here")
                }
            }
        } catch (e: Throwable) {
            log(TAG, WARN) { "Export to $savePath failed, removing the incomplete document: ${e.asLog()}" }
            try {
                savePath.delete()
            } catch (de: Exception) {
                log(TAG, WARN) { "Failed to remove incomplete $savePath: ${de.asLog()}" }
            }
            throw e
        }

        val exportedSize = savePath.length
        log(TAG, INFO) { "Exported size is $exportedSize" }

        return Result(
            installId = target.installId,
            baseApk = baseApk,
            extraSources = extraSources,
            savePath = savePath.uri,
            exportSize = exportedSize,
        )
    }

    /**
     * Creates the export document under a name that is still free, counter before the extension.
     *
     * The listing only advises: `DocumentsProvider` may hand back a document under a different
     * display name to dodge a conflict, so the created name is read back and a mangled one is
     * dropped again before the next attempt.
     */
    private fun createExportFile(
        saveDir: SAFDocFile,
        baseName: String,
        extension: String,
        mimeType: String,
    ): SAFDocFile {
        val suffix = ".$extension"

        for (attempt in 1..CREATE_ATTEMPTS) {
            val taken = saveDir.listChildDisplayNames()

            val candidate = (0..MAX_NAME_COUNTER)
                .asSequence()
                .map { counter -> if (counter == 0) "$baseName$suffix" else "$baseName ($counter)$suffix" }
                .firstOrNull { !taken.contains(it) }
                ?: throw IOException("No free name for '$baseName$suffix' in ${saveDir.uri}")

            val created = try {
                saveDir.createFile(mimeType, candidate)
            } catch (e: Exception) {
                if (!saveDir.listChildDisplayNames().contains(candidate)) throw e
                log(TAG, WARN) { "Attempt $attempt: '$candidate' was claimed while we created it, retrying" }
                continue
            }

            val createdName = created.name
            if (createdName != null && !createdName.endsWith(suffix)) {
                log(TAG, WARN) { "Attempt $attempt: provider renamed '$candidate' to '$createdName', retrying" }
                if (!created.delete()) log(TAG, WARN) { "Failed to delete mangled $created" }
                continue
            }
            if (createdName == null) log(TAG, WARN) { "Can't read back the name of $created, assuming '$candidate'" }

            log(TAG, INFO) { "Exporting to '$createdName' ($created)" }
            return created
        }

        throw IOException("Couldn't create '$baseName$suffix' in ${saveDir.uri} after $CREATE_ATTEMPTS attempts")
    }

    @Parcelize
    data class Result(
        val installId: InstallId,
        val baseApk: APath?,
        val extraSources: Set<APath>?,
        val savePath: Uri,
        val exportSize: Long,
    ) : Parcelable

    companion object {
        private val TAG = logTag("AppControl", "ExportSaver")
        private const val EXTENSION_APK = "apk"
        private const val EXTENSION_BUNDLE = "apks"
        private const val CREATE_ATTEMPTS = 3
        private const val MAX_NAME_COUNTER = 999
    }

}
