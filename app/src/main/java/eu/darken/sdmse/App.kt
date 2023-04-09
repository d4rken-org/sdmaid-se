package eu.darken.sdmse

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.AutomaticBugReporter
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.autoreport.DebugSettings
import eu.darken.sdmse.common.debug.logging.*
import eu.darken.sdmse.common.debug.recording.core.RecorderModule
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.updater.UpdateChecker
import eu.darken.sdmse.main.core.CurriculumVitae
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.ThemeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltAndroidApp
open class App : Application(), Configuration.Provider {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var bugReporter: AutomaticBugReporter
    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var recorderModule: RecorderModule
    @Inject lateinit var imageLoaderFactory: ImageLoaderFactory
    @Inject lateinit var debugSettings: DebugSettings
    @Inject lateinit var curriculumVitae: CurriculumVitae
    @Inject lateinit var updateChecker: UpdateChecker

    val logCatLogger = LogCatLogger()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfigWrap.DEBUG) {
            Logging.install(logCatLogger)
            log(TAG) { "BuildConfig.DEBUG=true" }
        }
        log(TAG) { "Fingerprint: ${BuildWrap.FINGERPRINT}" }

        combine(
            debugSettings.isDebugMode.flow,
            debugSettings.isTraceMode.flow,
            debugSettings.isDryRunMode.flow,
            recorderModule.state,
        ) { isDebug, isTrace, isDryRun, recorder ->
            log(TAG) { "isDebug=$isDebug, isTrace=$isTrace, isDryRun=$isDryRun, recorder=$recorder" }
            if (!BuildConfigWrap.DEBUG) {
                if (isDebug) {
                    Logging.install(logCatLogger)
                } else {
                    Logging.remove(logCatLogger)
                }
            }

            Bugs.isDebug = isDebug || recorder.isRecording
            Bugs.isTrace = isDebug && isTrace
            Bugs.isDryRun = isDryRun
        }.launchIn(appScope)

        bugReporter.setup(this)

        recorderModule.state
            .onEach { log(TAG) { "RecorderModule: $it" } }
            .launchIn(appScope)

        generalSettings.themeType.flow
            .map { ThemeType.valueOf(it) }
            .onEach {
                withContext(dispatcherProvider.Main) {
                    when (it) {
                        ThemeType.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        ThemeType.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        ThemeType.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                }
            }
            .setupCommonEventHandlers(TAG) { "themeMode" }
            .launchIn(appScope)

        Coil.setImageLoader(imageLoaderFactory)

        curriculumVitae.updateAppLaunch()

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }

        appScope.launch {
            log { "${updateChecker.getLatest(UpdateChecker.Channel.BETA)}" }
            log { "${updateChecker.getLatest(UpdateChecker.Channel.PROD)}" }
        }
    }


    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setMinimumLoggingLevel(
            when {
                BuildConfigWrap.DEBUG -> android.util.Log.VERBOSE
                BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.DEV -> android.util.Log.DEBUG
                BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.BETA -> android.util.Log.INFO
                BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE -> android.util.Log.WARN
                else -> android.util.Log.VERBOSE
            }
        )
        .setWorkerFactory(workerFactory)
        .build()

    companion object {
        internal val TAG = logTag("App")
    }
}
