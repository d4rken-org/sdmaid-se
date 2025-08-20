package eu.darken.sdmse.common.debug.recorder.ui


import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.compression.Zipper
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.deleteAll
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.uix.ViewModel3
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @param:ApplicationContext private val context: Context,
    private val webpageTool: WebpageTool,
) : ViewModel3(dispatcherProvider) {

    private val recordedPath = handle.get<String>(RecorderActivity.RECORD_PATH)?.let { File(it) }

    private val stater = DynamicStateFlow(TAG, vmScope) {
        State(logDir = recordedPath)
    }
    val state = stater.asLiveData2()

    val shareEvent = SingleLiveEvent<Intent>()

    init {
        launch {
            if (recordedPath == null) throw IllegalStateException("No recorded path found")

            log(TAG) { "Getting log files in dir: $recordedPath" }
            val logFiles = recordedPath.listFiles() ?: throw IllegalStateException("No log files found")

            log(TAG) { "Found ${logFiles.size} logfiles: $logFiles" }
            var entries = logFiles.map { LogFileAdapter.Entry.Item(path = it) }
            stater.updateBlocking { copy(logEntries = entries) }

            log(TAG) { "Determining log file size..." }
            entries = entries.map { entry -> entry.copy(size = entry.path.length()) }.sortedByDescending { it.size }
            stater.updateBlocking { copy(logEntries = entries) }

            log(TAG) { "Compressing log files..." }
            val zipFile = File(recordedPath.parentFile, "${recordedPath.name}.zip")
            log(TAG) { "Writing zip file to $zipFile" }
            Zipper().zip(
                entries.map { it.path.path },
                zipFile.path
            )
            val zippedSize = zipFile.length()
            log(TAG) { "Zip file created ${zippedSize}B at $zipFile" }
            stater.updateBlocking { copy(compressedFile = zipFile, compressedSize = zippedSize, isWorking = false) }
        }
    }

    fun share() = launch {
        val file = stater.value().compressedFile ?: throw IllegalStateException("compressedFile is null")

        val intent = Intent(Intent.ACTION_SEND).apply {
            val uri = FileProvider.getUriForFile(
                context,
                BuildConfigWrap.APPLICATION_ID + ".provider",
                file
            )

            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            type = "application/zip"

            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "${BuildConfigWrap.APPLICATION_ID} DebugLog - ${BuildConfigWrap.VERSION_DESCRIPTION})"
            )
            putExtra(Intent.EXTRA_TEXT, "Your text here.")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }


        val chooserIntent = Intent.createChooser(intent, context.getString(R.string.debug_debuglog_file_label))
        shareEvent.postValue(chooserIntent)
    }

    fun goPrivacyPolicy() {
        webpageTool.open(SdmSeLinks.PRIVACY_POLICY)
    }

    fun discard() = launch {
        stater.updateBlocking { copy(isWorking = true) }
        recordedPath?.deleteAll()
        popNavStack()
    }

    data class State(
        val logDir: File?,
        val logEntries: List<LogFileAdapter.Entry.Item> = emptyList(),
        val compressedFile: File? = null,
        val compressedSize: Long? = null,
        val isWorking: Boolean = true,
    )

    companion object {
        private val TAG = logTag("Debug", "Recorder", "ViewModel")
    }
}