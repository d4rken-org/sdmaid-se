package eu.darken.sdmse.common.debug.recorder.core

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.compression.Zipper
import java.io.File
import javax.inject.Inject

@Reusable
class DebugLogZipper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun zipAndGetUri(logDir: File): Uri {
        val logFiles = logDir.listFiles()?.toList()
            ?: throw IllegalStateException("No log files in $logDir")

        val zipFile = File(logDir.parentFile, "${logDir.name}.zip")
        Zipper().zip(logFiles.map { it.path }, zipFile.path)

        return FileProvider.getUriForFile(
            context,
            BuildConfigWrap.APPLICATION_ID + ".provider",
            zipFile,
        )
    }

    fun getUriForZip(zipFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            BuildConfigWrap.APPLICATION_ID + ".provider",
            zipFile,
        )
    }

}
