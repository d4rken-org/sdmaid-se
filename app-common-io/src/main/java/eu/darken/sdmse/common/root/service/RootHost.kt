package eu.darken.sdmse.common.root.service

import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.Keep
import dagger.Lazy
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.FileLogger
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.service.internal.BaseRootHost
import eu.darken.sdmse.common.root.service.internal.RootIPC
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.Resource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import eu.darken.sdmse.common.shell.SharedShell
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.util.concurrent.TimeoutException
import javax.inject.Inject


/**
 * This class' main method will be launched as root. You can access any other class from your
 * package, but not instances - this is a separate process from the UI.
 */
@Keep
@SuppressLint("UnsafeDynamicallyLoadedCode")
class RootHost constructor(_args: List<String>) : HasSharedResource<Any>, BaseRootHost("$TAG#${hashCode()}", _args) {

    override val sharedResource = SharedResource.createKeepAlive(iTag, hostScope)

    lateinit var component: RootComponent

    @Inject lateinit var sharedShell: SharedShell
    @Inject lateinit var serviceHost: Lazy<RootServiceHost>
    @Inject lateinit var rootIpcFactory: RootIPC.Factory

    override suspend fun onInit() {
        component = DaggerRootComponent.builder().application(systemContext).build().also {
            it.inject(this)
        }
    }

    override suspend fun onExecute() {
        log(iTag) { "Starting IPC connection via $rootIpcFactory" }
        val ipc = rootIpcFactory.create(
            initArgs = initOptions,
            userProvidedBinder = serviceHost.get(),
        )
        log(iTag) { "IPC created: $ipc" }

        var currentFileLogger: FileLogger? = null

        ipc.hostOptions
            .onEach { options ->
                if (options.isDebug && Logging.loggers.none { it == logCatLogger }) {
                    Logging.install(logCatLogger)
                    log(TAG) { "Logger installed!" }
                } else if (!options.isDebug) {
                    log(TAG) { "Logger will be removed now!" }
                    Logging.remove(logCatLogger)
                }

                if (options.recorderPath != null && currentFileLogger == null) {
                    val logger = FileLogger(File(options.recorderPath + "_root")).also {
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

        val keepAliveToken: Resource<*> = sharedResource.get()

        log(iTag) { "Launching SharedShell with root" }
        adoptChildResource(sharedShell)

        try {
            log(iTag) { "Ready, now broadcasting..." }
            ipc.broadcastAndWait()
        } catch (e: TimeoutException) {
            log(iTag, ERROR) { "Non-root process did not connect in a timely fashion" }
        }

        keepAliveToken.close()
    }

    @Keep
    companion object {
        internal val TAG = logTag("Root", "Host")

        @Keep
        @JvmStatic
        fun main(args: Array<String>) {
            Bugs.processTag = "Root"
            Log.v(TAG, "main(args=$args)")
            RootHost(args.toList()).start()
        }
    }
}
