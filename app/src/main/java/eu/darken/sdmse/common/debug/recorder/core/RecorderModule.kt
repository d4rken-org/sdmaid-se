package eu.darken.sdmse.common.debug.recorder.core

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.SDMId
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.DebugSettings
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.ui.RecorderActivity
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.getPackageInfo
import eu.darken.sdmse.common.startServiceCompat
import eu.darken.sdmse.main.core.CurriculumVitae
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecorderModule @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val dataAreaManager: DataAreaManager,
    private val sdmId: SDMId,
    private val debugSettings: DebugSettings,
    private val curriculumVitae: CurriculumVitae,
) {

    private val triggerFile by lazy {
        try {
            File(context.getExternalFilesDir(null), FORCE_FILE)
        } catch (e: Exception) {
            File(
                Environment.getExternalStorageDirectory(),
                "/Android/data/${BuildConfigWrap.APPLICATION_ID}/files/$FORCE_FILE"
            )
        }
    }

    private val internalState = DynamicStateFlow(TAG, appScope + dispatcherProvider.IO) {
        val triggerFileExists = triggerFile.exists()
        State(shouldRecord = triggerFileExists || debugSettings.recorderPath.value() != null)
    }
    val state: Flow<State> = internalState.flow

    init {
        internalState.flow
            .onEach {
                log(TAG) { "New Recorder state: $internalState" }

                internalState.updateBlocking {
                    if (!isRecording && shouldRecord) {

                        val logPath = debugSettings.recorderPath.value()?.let {
                            log(TAG) { "Continuing existing log: $it" }
                            File(it)
                        } ?: createRecordingFilePath().also {
                            log(TAG) { "Starting new log: ${it.path}" }
                            debugSettings.recorderPath.value(it.path)
                        }

                        val newRecorder = Recorder().apply { start(logPath) }

                        if (!triggerFile.exists()) triggerFile.createNewFile()

                        logInfos()

                        context.startServiceCompat(Intent(context, RecorderService::class.java))

                        copy(
                            recorder = newRecorder
                        )
                    } else if (!shouldRecord && isRecording) {
                        val currentLog = recorder!!.path!!
                        log(TAG) { "Stopping log recorder for: $currentLog" }
                        recorder.stop()

                        debugSettings.recorderPath.value(null)
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
            .catch { log(TAG, ERROR) { "Log recording failed: ${it.asLog()}" } }
            .launchIn(appScope)
    }

    private fun createRecordingFilePath() = File(
        File(context.externalCacheDir, "debug/logs"),
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

    private suspend fun logInfos() {
        val pkgInfo = context.getPackageInfo()
        log(TAG, INFO) { "APILEVEL: ${BuildWrap.VERSION.SDK_INT}" }
        log(TAG, INFO) { "Build.FINGERPRINT: ${BuildWrap.FINGERPRINT}" }
        log(TAG, INFO) { "Build.MANUFACTOR: ${Build.MANUFACTURER}" }
        log(TAG, INFO) { "Build.BRAND: ${Build.BRAND}" }
        log(TAG, INFO) { "Build.PRODUCT: ${Build.PRODUCT}" }
        val versionInfo = "${pkgInfo.versionName} (${pkgInfo.versionCode})"
        log(TAG, INFO) { "App: ${context.packageName} - $versionInfo " }
        log(TAG, INFO) { "Build: ${BuildConfigWrap.FLAVOR}-${BuildConfigWrap.BUILD_TYPE}" }

        val installID = sdmId.id
        log(TAG, INFO) { "Install ID: $installID" }

        val locales = Resources.getSystem().configuration.locales
        log(TAG, INFO) { "App locales: $locales" }

        val state = dataAreaManager.latestState.firstOrNull()
        log(TAG, INFO) { "Data areas: (${state?.areas?.size})" }
        state?.areas?.forEachIndexed { index, dataArea -> log(TAG, INFO) { "#$index $dataArea" } }

        log(TAG, INFO) { "Update history: ${curriculumVitae.history.firstOrNull()}" }
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