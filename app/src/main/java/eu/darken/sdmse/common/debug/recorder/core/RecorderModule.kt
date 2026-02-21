package eu.darken.sdmse.common.debug.recorder.core

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Environment
import androidx.core.content.pm.PackageInfoCompat
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
        } catch (_: Exception) {
            File(
                Environment.getExternalStorageDirectory(),
                "/Android/data/${BuildConfigWrap.APPLICATION_ID}/files/$FORCE_FILE"
            )
        }
    }

    private val internalState = DynamicStateFlow(TAG, appScope + dispatcherProvider.IO) {
        val triggerFileExists = triggerFile.exists()
        val savedPath = debugSettings.recorderPath.value()
        State(
            shouldRecord = triggerFileExists || savedPath != null,
        )
    }
    val state: Flow<State> = internalState.flow

    init {
        internalState.flow
            .onEach {
                log(TAG) { "New Recorder state: $internalState" }

                internalState.updateBlocking {
                    if (!isRecording && shouldRecord) {
                        val logDir = debugSettings.recorderPath.value()?.let {
                            log(TAG) { "Continuing existing log: $it" }
                            File(it)
                        } ?: createRecordingDir().also {
                            log(TAG) { "Starting new log: $it" }
                            debugSettings.recorderPath.value(it.path)
                        }

                        val newRecorder = Recorder().apply { start(logDir) }

                        if (!triggerFile.exists()) triggerFile.createNewFile()

                        logInfos()

                        context.startServiceCompat(Intent(context, RecorderService::class.java))

                        copy(
                            recorder = newRecorder,
                            currentLogDir = logDir,
                        )
                    } else if (!shouldRecord && isRecording) {
                        log(TAG) { "Stopping log recorder for: $currentLogDir" }
                        recorder!!.stop()

                        debugSettings.recorderPath.value(null)
                        if (triggerFile.exists() && !triggerFile.delete()) {
                            log(TAG, ERROR) { "Failed to delete trigger file" }
                        }

                        val intent = RecorderActivity.getLaunchIntent(context, currentLogDir!!.path).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)

                        copy(
                            recorder = null,
                        )
                    } else {
                        this
                    }
                }
            }
            .catch { log(TAG, ERROR) { "Log recording failed: ${it.asLog()}" } }
            .launchIn(appScope)
    }

    private fun createRecordingDir(): File {
        val pkg = BuildConfigWrap.APPLICATION_ID
        val version = BuildConfigWrap.VERSION_CODE
        val timestamp = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())

        val logId = sdmId.id.take(4)
        var sessionDir: File? = null

        File(File(context.getExternalFilesDir(null), "debug/logs"), "${pkg}_${version}_${timestamp}_$logId").apply {
            @Suppress("SetWorldWritable", "SetWorldReadable")
            if (mkdirs()) {
                log(TAG) { "Public session dir created" }
                if (setReadable(true, false)) log(TAG) { "Session dir is readable" }
                if (setWritable(true, false)) log(TAG) { "Session dir is writeable" }
                sessionDir = this
            } else {
                log(TAG, ERROR) { "Failed to create public session dir" }
            }
        }

        if (sessionDir == null) {
            sessionDir = File(File(context.cacheDir, "debug/logs"), "${pkg}_${version}_${timestamp}").apply {
                if (mkdirs()) {
                    log(TAG) { "Private session dir created" }
                } else {
                    log(TAG, ERROR) { "Failed to create private session dir" }
                }
            }
        }

        return sessionDir
    }

    suspend fun startRecorder(): File {
        internalState.updateBlocking {
            copy(shouldRecord = true)
        }
        return internalState.flow.filter { it.isRecording }.first().currentLogDir!!
    }

    suspend fun stopRecorder(): File? {
        val currentPath = internalState.value().currentLogDir ?: return null
        internalState.updateBlocking {
            copy(shouldRecord = false)
        }
        internalState.flow.filter { !it.isRecording }.first()
        return currentPath
    }

    fun getLogDirectories(): List<File> {
        val dirs = mutableListOf<File>()

        // Primary location: external files dir
        context.getExternalFilesDir(null)?.let { externalDir ->
            File(externalDir, "debug/logs").takeIf { it.exists() }?.let { dirs.add(it) }
        }

        // Fallback location: cache dir
        File(context.cacheDir, "debug/logs").takeIf { it.exists() }?.let { dirs.add(it) }

        return dirs
    }

    private suspend fun logInfos() {
        val pkgInfo = context.getPackageInfo()
        log(TAG, INFO) { "APILEVEL: ${BuildWrap.VERSION.SDK_INT}" }
        log(TAG, INFO) { "Build.FINGERPRINT: ${BuildWrap.FINGERPRINT}" }
        log(TAG, INFO) { "Build.MANUFACTOR: ${Build.MANUFACTURER}" }
        log(TAG, INFO) { "Build.BRAND: ${Build.BRAND}" }
        log(TAG, INFO) { "Build.PRODUCT: ${Build.PRODUCT}" }
        val versionInfo = "${pkgInfo.versionName} (${PackageInfoCompat.getLongVersionCode(pkgInfo)})"
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
        val currentLogDir: File? = null,
    ) {
        val isRecording: Boolean
            get() = recorder != null
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder", "Module")
        private const val FORCE_FILE = "force_debug_run"
    }
}