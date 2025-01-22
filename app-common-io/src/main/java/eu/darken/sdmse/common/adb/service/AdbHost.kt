package eu.darken.sdmse.common.adb.service

import android.content.Context
import android.os.IBinder
import androidx.annotation.Keep
import dagger.Lazy
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.adb.service.internal.BaseAdbHost
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.FileLogger
import eu.darken.sdmse.common.debug.logging.LogCatLogger
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.Resource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shell.SharedShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject

@Keep
class AdbHost(
    context: Context
) : BaseAdbHost(TAG, context), HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, hostScope)

    private lateinit var component: AdbComponent
    private lateinit var keepAliveToken: Resource<*>

    @Inject lateinit var sharedShell: SharedShell
    @Inject lateinit var serviceHost: Lazy<AdbServiceHost>

    private val logCatLogger = LogCatLogger()
    private val currentOptions = MutableStateFlow(AdbHostOptions())

    init {
        Bugs.processTag = "ADB"
        if (BuildConfigWrap.DEBUG) {
            Logging.install(logCatLogger)
            log(TAG) { "BuildConfigWrap.DEBUG=true" }
        }
        log(TAG, INFO) { "init()" }

        runBlocking { onStart() }
    }

    suspend fun onStart() {
        component = DaggerAdbComponent.builder().application(context).build().also {
            it.inject(this)
        }

        keepAliveToken = sharedResource.get()

        var currentFileLogger: FileLogger? = null

        currentOptions
            .onEach { options ->
                log(TAG) { "New options: $options" }
                if (options.isDebug && Logging.loggers.none { it == logCatLogger }) {
                    Logging.install(logCatLogger)
                    log(TAG) { "Logger installed!" }
                } else if (!options.isDebug) {
                    log(TAG) { "Logger will be removed now!" }
                    Logging.remove(logCatLogger)
                }

                if (options.recorderPath != null && currentFileLogger == null) {
                    val ogPath = options.recorderPath!!
                    val newPath = ogPath.replace(".log", "_adb.log")
                    val logger = FileLogger(File(newPath)).also {
                        currentFileLogger = it
                        it.start()
                    }
                    Logging.install(logger)
                    log(TAG) { "FileLogger installed" }
                } else if (options.recorderPath == null && currentFileLogger != null) {
                    log(TAG) { "Removing FileLogger: $currentFileLogger" }
                    currentFileLogger?.let { Logging.remove(it) }
                    currentFileLogger = null
                }

                Bugs.isDebug = options.isDebug
                Bugs.isTrace = options.isTrace
                Bugs.isDryRun = options.isDryRun
            }
            .launchIn(hostScope)
    }

    override suspend fun onDestroy() {
        keepAliveToken.close()
    }

    override fun getUserConnection(): IBinder = serviceHost.get()

    override fun updateHostOptions(options: AdbHostOptions) {
        log(TAG) { "updateHostOptions(): $options" }
        currentOptions.value = options
    }

    companion object {
        internal val TAG = logTag("ADB", "Host")
    }
}