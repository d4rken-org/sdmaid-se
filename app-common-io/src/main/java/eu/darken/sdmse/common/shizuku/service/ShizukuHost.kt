package eu.darken.sdmse.common.shizuku.service

import android.content.Context
import android.os.IBinder
import androidx.annotation.Keep
import dagger.Lazy
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.LogCatLogger
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.Resource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shell.SharedShell
import eu.darken.sdmse.common.shizuku.service.internal.BaseShizukuHost
import eu.darken.sdmse.common.shizuku.service.internal.ShizukuHostOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@Keep
class ShizukuHost(
    context: Context
) : BaseShizukuHost(TAG, context), HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, hostScope)

    private lateinit var component: ShizukuComponent
    private lateinit var keepAliveToken: Resource<*>

    @Inject lateinit var sharedShell: SharedShell
    @Inject lateinit var serviceHost: Lazy<ShizukuServiceHost>

    private val logCatLogger = LogCatLogger()
    private val currentOptions = MutableStateFlow(ShizukuHostOptions())

    init {
        if (BuildConfigWrap.DEBUG) {
            Logging.install(logCatLogger)
            log(TAG) { "BuildConfigWrap.DEBUG=true" }
        }
        log(TAG, INFO) { "init()" }

        runBlocking { onStart() }
    }

    suspend fun onStart() {
        component = DaggerShizukuComponent.builder().application(context).build().also {
            it.inject(this)
        }

        keepAliveToken = sharedResource.get()

        currentOptions
            .onEach {
                Bugs.isDebug = it.isDebug
                Bugs.isTrace = it.isTrace
                Bugs.isDryRun = it.isDryRun

                when {
                    it.isDebug && Logging.loggers.none { it == logCatLogger } -> Logging.install(logCatLogger)
                    !it.isDebug -> Logging.remove(logCatLogger)
                }
            }
            .launchIn(hostScope)
    }

    override suspend fun onDestroy() {
        keepAliveToken.close()
    }

    override fun getUserConnection(): IBinder = serviceHost.get()

    override fun updateHostOptions(options: ShizukuHostOptions) {
        log(TAG) { "updateHostOptions(): $options" }
        currentOptions.value = options
    }

    companion object {
        internal val TAG = logTag("Shizuku", "Host")
    }
}