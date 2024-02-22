package eu.darken.sdmse.appcontrol.core.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.common.MimeTypes
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compression.Zipper
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.FileMode
import eu.darken.sdmse.common.files.saf.SAFDocFile
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.pkgs.features.Installed
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

        val baseApk = target.pkg.sourceDir
        log(TAG) { "Base APK is $baseApk" }

        val extraSources = target.pkg.splitSources
        log(TAG) { "Split sources are $extraSources" }


        val name = "${target.label.get(context)} (${target.installId.pkgId.name})"
        val version = "${target.pkg.versionName}[${target.pkg.versionCode}]"
        val extension = when (target.exportType) {
            AppExportType.APK -> "apk"
            AppExportType.BUNDLE -> "apks"
        }
        val finalName = "$name - $version.$extension"

        val saveDir = SAFDocFile.fromTreeUri(context, contentResolver, directoryUri)

        val existing = saveDir.findFile(finalName)
        if (existing?.exists == true) log(TAG, WARN) { "Already exists: ${existing.uri}" }

        // This, or the underlying SAF implementation, appends "(1)" if the file already exists
        val savePath = saveDir.createFile(MimeTypes.Zip.value, finalName)
        if (!savePath.writable) throw IOException("$savePath is not writable")

        val pfd = savePath
            .openPFD(contentResolver, FileMode.WRITE)
            .let { ParcelFileDescriptor.AutoCloseOutputStream(it) }

        when (target.exportType) {
            AppExportType.APK -> {
                if (baseApk == null) throw IllegalStateException("APK file unavilable")
                updateProgressPrimary(baseApk.userReadablePath)
                pfd.sink().buffer().use { sink ->
                    (baseApk as LocalPath).file.source().buffer().use { source ->
                        sink.writeAll(source)
                    }
                }
            }

            AppExportType.BUNDLE -> { // Create ZIP
                val entries = mutableSetOf<APath>()
                if (baseApk != null) entries.add(baseApk)
                if (extraSources != null) entries.addAll(extraSources)

                if (entries.isEmpty()) throw IllegalStateException("BUNDLE is empty")

                ZipOutputStream(pfd.sink().buffer().outputStream()).use { zipOut ->
                    entries.forEach { file ->
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

    @Parcelize
    data class Result(
        val installId: Installed.InstallId,
        val baseApk: APath?,
        val extraSources: Set<APath>?,
        val savePath: Uri,
        val exportSize: Long,
    ) : Parcelable

    companion object {
        private val TAG = logTag("AppControl", "ExportSaver")
    }

}