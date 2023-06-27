package eu.darken.sdmse.common.root

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.shareLatest
import eu.darken.sdmse.common.root.service.RootServiceClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
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
    val serviceClient: RootServiceClient,
    settings: RootSettings,
) {

    private var cachedState: Boolean? = null
    private val cacheLock = Mutex()

    init {
        settings.useRoot.flow
            .mapLatest {
                log(TAG) { "Root access state: $it" }
                cacheLock.withLock {
                    cachedState = null
                }
            }
            .launchIn(appScope)
    }

    /**
     * Is the device rooted and we have access?
     */
    suspend fun isRooted(): Boolean = withContext(dispatcherProvider.IO) {
        cacheLock.withLock {
            cachedState?.let { return@withContext it }

            val newState = try {
                serviceClient.get().item.ipc.checkBase() != null
            } catch (e: Exception) {
                log(TAG, WARN) { "Error while checking for root: $e" }
                false
            }

            newState.also { cachedState = it }
        }
    }

    /**
     * Did the user consent to SD Maid using root and is root available?
     */
    val useRoot: Flow<Boolean> = settings.useRoot.flow
        .mapLatest { (it ?: false) && isRooted() }
        .shareLatest(appScope)

    companion object {
        internal val TAG = logTag("Root", "Manager")
    }
}
