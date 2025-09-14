package eu.darken.sdmse.common.funnel

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tries to reduce the chance that we hit the IPC buffer limit.
 * Hitting the buffer limit can result in crashes or more grave incomplete results.
 */
@Singleton
class IPCFunnel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val execLock = Semaphore(
        when {
            hasApiLevel(31) -> 3
            hasApiLevel(29) -> 2
            else -> 1
        }.also { log { "IPCFunnel init with parallelization set to $it" } }
    )

    private val funnelEnv by lazy {
        object : FunnelEnvironment {
            override val packageManager: PackageManager = context.packageManager
        }
    }

    init {
        log(TAG) { "IPCFunnel initialized." }
    }

    suspend fun <T> use(block: suspend FunnelEnvironment.() -> T): T = withContext(dispatcherProvider.IO) {
        execLock.withPermit {
            block(funnelEnv)
        }
    }

    interface FunnelEnvironment {
        val packageManager: PackageManager
    }

    companion object {
        internal val TAG = logTag("IPCFunnel")
    }
}
