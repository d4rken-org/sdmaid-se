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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.flow.onError
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val webpageTool: WebpageTool,
) : ViewModel3(dispatcherProvider) {

    private val recordedPath = handle.get<String>(RecorderActivity.RECORD_PATH)!!
    private val pathCache = MutableStateFlow(recordedPath)

    data class LogData(
        val file: File,
        val size: Long,
    )

    private val logObsDefault = pathCache
        .map { File(it) }
        .map { LogData(it, it.length()) }
        .catch { log(TAG, ERROR) { "Failed to get default log size: ${it.asLog()}" } }
        .replayingShare(vmScope)

    private val logObsShizuku = pathCache
        .map { File(it + "_shizuku") }
        .map { if (it.exists()) LogData(it, it.length()) else null }
        .catch { log(TAG, ERROR) { "Failed to get Shizuku log size: ${it.asLog()}" } }
        .replayingShare(vmScope)

    private val logObsRoot = pathCache
        .map { File(it + "_root") }
        .map { if (it.exists()) LogData(it, it.length()) else null }
        .catch { log(TAG, ERROR) { "Failed to get root log size: ${it.asLog()}" } }
        .replayingShare(vmScope)

    private val resultCacheCompressedObs = combine(
        logObsDefault,
        logObsShizuku,
        logObsRoot,
    ) { default, shizuku, root ->
        val zipContent = listOfNotNull(
            default.file.path,
            shizuku?.file?.path,
            root?.file?.path
        )
        val zipFile = File("${default.file.path}.zip")
        Zipper().zip(zipContent, zipFile.path)
        zipFile to zipFile.length()
    }
        .catch { log(TAG, ERROR) { "Failed to compress log: ${it.asLog()}" } }
        .replayingShare(vmScope + dispatcherProvider.IO)

    private val stater = DynamicStateFlow(TAG, vmScope) { State() }
    val state = stater.asLiveData2()

    val shareEvent = SingleLiveEvent<Intent>()

    init {
        logObsDefault
            .onEach { (path, size) ->
                stater.updateBlocking { copy(normalPath = path, normalSize = size) }
            }
            .launchInViewModel()

        resultCacheCompressedObs
            .onEach { (path, size) ->
                stater.updateBlocking {
                    copy(
                        compressedPath = path,
                        compressedSize = size,
                        loading = false
                    )
                }
            }
            .onError { errorEvents.postValue(it) }
            .launchInViewModel()

    }

    fun share() = launch {
        val (file, size) = resultCacheCompressedObs.first()

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

    data class State(
        val normalPath: File? = null,
        val normalSize: Long = -1L,
        val compressedPath: File? = null,
        val compressedSize: Long = -1L,
        val loading: Boolean = true
    )

    companion object {
        private val TAG = logTag("Debug", "Recorder", "ViewModel")
    }
}