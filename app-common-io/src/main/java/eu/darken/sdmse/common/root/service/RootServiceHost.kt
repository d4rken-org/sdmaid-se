package eu.darken.sdmse.common.root.service

import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.Keep
import dagger.Lazy
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.service.internal.RootHost
import eu.darken.sdmse.common.root.service.internal.RootIPC
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.Resource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import eu.darken.sdmse.common.shell.RootProcessShell
import eu.darken.sdmse.common.shell.SharedShell
import java.util.concurrent.TimeoutException
import javax.inject.Inject


/**
 * This class' main method will be launched as root. You can access any other class from your
 * package, but not instances - this is a separate process from the UI.
 */
@Keep
@SuppressLint("UnsafeDynamicallyLoadedCode")
class RootServiceHost constructor(_args: List<String>) : HasSharedResource<Any>, RootHost("$TAG#${hashCode()}", _args) {

    override val sharedResource = SharedResource.createKeepAlive(iTag, rootHostScope)

    lateinit var component: RootComponent

    @RootProcessShell @Inject lateinit var sharedShell: SharedShell
    @Inject lateinit var connection: Lazy<RootServiceConnectionImpl>
    @Inject lateinit var rootIpcFactory: RootIPC.Factory

    override suspend fun onInit() {
        if (isDebug) {
            Bugs.isDebug = true
        }

        component = DaggerRootComponent.builder().application(systemContext).build().also {
            it.inject(this)
        }

        log(iTag) { "Running on threadId=${Thread.currentThread().id}" }
    }

    override suspend fun onExecute() {
        log(iTag) { "Starting IPC connection via $rootIpcFactory" }
        val ipc = rootIpcFactory.create(
            packageName = BuildConfigWrap.APPLICATION_ID,
            userProvidedBinder = connection.get(),
            pairingCode = pairingCode,
        )
        log(iTag) { "IPC created: $ipc" }

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
        internal val TAG = logTag("Root", "Java", "Host")

        @Keep
        @JvmStatic
        fun main(args: Array<String>) {
            Log.v(TAG, "main(args=$args)")
            RootServiceHost(args.toList()).start()
        }
    }
}
