package eu.darken.sdmse.common.debug.recorder.core

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.compression.Zipper
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import javax.inject.Inject

@Reusable
class DebugLogZipper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun zip(logDir: File): File {
        val logFiles = logDir.listFiles()?.filter { it.isFile }
            ?: throw IllegalStateException("No log files in $logDir")
        require(logFiles.isNotEmpty()) { "No log files in $logDir" }

        val zipFile = File(logDir.parentFile, "${logDir.name}.zip")
        val tempFile = File(logDir.parentFile, "${logDir.name}.zip.tmp")

        try {
            Zipper().zip(logFiles.map { it.path }, tempFile.path)
            try {
                Files.move(
                    tempFile.toPath(),
                    zipFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    zipFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            tempFile.delete()
        }

        return zipFile
    }

    fun zipAndGetUri(logDir: File): Uri {
        val zipFile = zip(logDir)
        return getUriForZip(zipFile)
    }

    fun getUriForZip(zipFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            BuildConfigWrap.APPLICATION_ID + ".provider",
            zipFile,
        )
    }
}
