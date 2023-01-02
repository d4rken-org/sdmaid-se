package eu.darken.sdmse.common.root

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.javaroot.JavaRootClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val javaRootClient: JavaRootClient,
    private val rootSettings: RootSettings,
) {

    private var cachedState: Boolean? = null
    private val cacheLock = Mutex()

    init {
        rootSettings.useRoot.flow
            .mapLatest {
                cacheLock.withLock {
                    cachedState = null
                }
            }
            .launchIn(appScope)
    }

    suspend fun isRooted(): Boolean = withContext(dispatcherProvider.IO) {
        if (rootSettings.useRoot.value() != true) {
            log(TAG) { "Root aceess is disabled." }
            return@withContext false
        }

        cacheLock.withLock {
            cachedState?.let { return@withContext it }

            val newState = try {
                javaRootClient.get().item.ipc.checkBase() != null
            } catch (e: Exception) {
                log(TAG, ERROR) { "Error while checking for root: ${e.asLog()}" }
                false
            }

            newState.also { cachedState = it }
        }
    }

    suspend fun hasRoot(): Boolean {
        return (rootSettings.useRoot.value() ?: false) && isRooted()
    }

    companion object {
        internal val TAG = logTag("Root", "Manager")
    }
}
