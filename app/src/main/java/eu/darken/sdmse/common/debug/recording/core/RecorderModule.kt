package eu.darken.sdmse.common.debug.recording.core

import android.content.Context
import android.content.Intent
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recording.ui.RecorderActivity
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.startServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecorderModule @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val triggerFile = try {
        File(context.getExternalFilesDir(null), FORCE_FILE)
    } catch (e: Exception) {
        File(
            Environment.getExternalStorageDirectory(),
            "/Android/data/${BuildConfigWrap.APPLICATION_ID}/files/$FORCE_FILE"
        )
    }

    private val internalState = DynamicStateFlow(TAG, appScope + dispatcherProvider.IO) {
        val triggerFileExists = triggerFile.exists()
        State(shouldRecord = triggerFileExists)
    }
    val state: Flow<State> = internalState.flow

    init {
        internalState.flow
            .onEach {
                log(TAG) { "New Recorder state: $internalState" }

                internalState.updateBlocking {
                    if (!isRecording && shouldRecord) {
                        val newRecorder = Recorder()
                        newRecorder.start(createRecordingFilePath())
                        triggerFile.createNewFile()

                        context.startServiceCompat(Intent(context, RecorderService::class.java))

                        copy(
                            recorder = newRecorder
                        )
                    } else if (!shouldRecord && isRecording) {
                        val currentLog = recorder!!.path!!
                        recorder.stop()

                        if (triggerFile.exists() && !triggerFile.delete()) {
                            log(TAG, ERROR) { "Failed to delete trigger file" }
                        }

                        val intent = RecorderActivity.getLaunchIntent(context, currentLog.path).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)

                        copy(
                            recorder = null,
                            lastLogPath = currentLog
                        )
                    } else {
                        this
                    }
                }
            }
            .launchIn(appScope)
    }

    private fun createRecordingFilePath() = File(
        File(context.cacheDir, "debug/logs"),
        "${BuildConfigWrap.APPLICATION_ID}_logfile_${System.currentTimeMillis()}.log"
    )

    suspend fun startRecorder(): File {
        internalState.updateBlocking {
            copy(shouldRecord = true)
        }
        return internalState.flow.filter { it.isRecording }.first().currentLogPath!!
    }

    suspend fun stopRecorder(): File? {
        val currentPath = internalState.value().currentLogPath ?: return null
        internalState.updateBlocking {
            copy(shouldRecord = false)
        }
        internalState.flow.filter { !it.isRecording }.first()
        return currentPath
    }

    data class State(
        val shouldRecord: Boolean = false,
        internal val recorder: Recorder? = null,
        val lastLogPath: File? = null,
    ) {
        val isRecording: Boolean
            get() = recorder != null

        val currentLogPath: File?
            get() = recorder?.path
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder", "Module")
        private const val FORCE_FILE = "force_debug_run"
    }
}