package eu.darken.sdmse.common.root.javaroot

import android.annotation.SuppressLint
import android.util.Log
import dagger.Lazy
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.javaroot.internal.RootHost
import eu.darken.sdmse.common.root.javaroot.internal.RootIPC
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.Resource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shell.RootProcessShell
import eu.darken.sdmse.common.shell.SharedShell
import java.util.concurrent.TimeoutException
import javax.inject.Inject


/**
 * This class' main method will be launched as root. You can access any other class from your
 * package, but not instances - this is a separate process from the UI.
 */
@SuppressLint("UnsafeDynamicallyLoadedCode")
class JavaRootHost constructor(_args: List<String>) : HasSharedResource<Any>, RootHost(TAG, _args) {

    override val sharedResource = SharedResource.createKeepAlive(
        TAG,
        rootHostScope
    )

    lateinit var component: RootComponent

    @RootProcessShell @Inject lateinit var sharedShell: SharedShell
    @Inject lateinit var connection: Lazy<JavaRootConnectionImpl>
    @Inject lateinit var rootIpcFactory: RootIPC.Factory

    override suspend fun onInit() {
        if (isDebug) {
            Bugs.isDebug = true
        }

        component = DaggerRootComponent.builder().application(systemContext).build().also {
            it.inject(this)
        }

        log(TAG) { "Running on threadId=${Thread.currentThread().id}" }
    }

    override suspend fun onExecute() {
        log(TAG) { "Starting IPC connection via $rootIpcFactory" }
        val ipc = rootIpcFactory.create(
            packageName = BuildConfigWrap.APPLICATION_ID,
            userProvidedBinder = connection.get(),
            pairingCode = pairingCode,
        )
        log(TAG) { "IPC created: $ipc" }

        val keepAliveToken: Resource<*> = sharedResource.get()

        log(TAG) { "Launching SharedShell with root" }
        sharedShell.addParent(this.sharedResource)

        try {
            log(TAG) { "Ready, now broadcasting..." }
            ipc.broadcastAndWait()
        } catch (e: TimeoutException) {
            log(TAG, ERROR) { "Non-root process did not connect in a timely fashion" }
        }

        keepAliveToken.close()
    }

    companion object {
        internal val TAG = logTag("Root", "Java", "Host")

        @SuppressLint("LogNotTimber")
        @JvmStatic
        fun main(args: Array<String>) {
            Log.v(TAG, "main(args=$args)")
            JavaRootHost(args.toList()).start()
        }
    }
}
