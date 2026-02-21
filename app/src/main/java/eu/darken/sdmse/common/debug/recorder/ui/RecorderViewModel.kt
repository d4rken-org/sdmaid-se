package eu.darken.sdmse.common.debug.recorder.ui


import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.DebugLogZipper
import eu.darken.sdmse.common.files.core.local.deleteAll
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.uix.ViewModel3
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val webpageTool: WebpageTool,
    private val debugLogZipper: DebugLogZipper,
) : ViewModel3(dispatcherProvider) {

    private val sessionPath = handle.get<String>(RecorderActivity.RECORD_PATH)?.let { File(it) }
    private val zipPath = sessionPath?.let { File(it.parentFile, "${it.name}.zip") }

    private val stater = DynamicStateFlow(TAG, vmScope) {
        State(logDir = sessionPath)
    }
    val state = stater.asLiveData2()

    val shareEvent = SingleLiveEvent<Intent>()

    init {
        launch {
            if (sessionPath == null) throw IllegalStateException("No recorded path found")

            log(TAG) { "Getting log files in dir: $sessionPath" }
            val logFiles = sessionPath.listFiles() ?: throw IllegalStateException("No log files found")

            log(TAG) { "Found ${logFiles.size} logfiles: $logFiles" }
            var entries = logFiles.map { LogFileAdapter.Entry.Item(path = it) }
            stater.updateBlocking { copy(logEntries = entries) }

            log(TAG) { "Determining log file size..." }
            entries = entries.map { entry -> entry.copy(size = entry.path.length()) }.sortedByDescending { it.size }
            stater.updateBlocking { copy(logEntries = entries) }

            log(TAG) { "Compressing log files..." }
            val uri = debugLogZipper.zipAndGetUri(sessionPath)
            val zipFile = zipPath ?: throw IllegalStateException("No zip path found")
            val zippedSize = zipFile.length()
            log(TAG) { "Zip file created ${zippedSize}B at $zipFile" }
            stater.updateBlocking { copy(compressedFile = zipFile, compressedSize = zippedSize, isWorking = false) }
        }
    }

    fun share() = launch {
        if (sessionPath == null) throw IllegalStateException("sessionPath is null")
        val uri = debugLogZipper.zipAndGetUri(sessionPath)

        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = "application/zip"

            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "${BuildConfigWrap.APPLICATION_ID} DebugLog - ${BuildConfigWrap.VERSION_DESCRIPTION}"
            )
            putExtra(Intent.EXTRA_TEXT, "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooserIntent = Intent.createChooser(intent, context.getString(R.string.debug_debuglog_file_label))
        shareEvent.postValue(chooserIntent)
    }

    fun goPrivacyPolicy() {
        webpageTool.open(SdmSeLinks.PRIVACY_POLICY)
    }

    fun keep() = launch {
        sessionPath?.deleteRecursively()
        popNavStack()
    }

    fun discard() = launch {
        stater.updateBlocking { copy(isWorking = true) }

        sessionPath?.deleteAll()
        if (sessionPath?.exists() == true) log(TAG, WARN) { "Failed to delete $sessionPath" }

        zipPath?.delete()
        if (zipPath?.exists() == true) log(TAG, WARN) { "Failed to delete $zipPath" }

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