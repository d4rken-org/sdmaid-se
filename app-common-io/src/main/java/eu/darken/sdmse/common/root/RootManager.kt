package eu.darken.sdmse.common.root

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.javaroot.JavaRootClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val javaRootClient: JavaRootClient,
) {

    private var cachedState: Boolean? = null
    private val cacheLock = Mutex()

    suspend fun isRooted(): Boolean = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "isRooted()" }

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

    companion object {
        internal val TAG = logTag("Root", "Manager")
    }
}
