package eu.darken.sdmse.common.root

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.root.service.RootServiceClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    val serviceClient: RootServiceClient,
    settings: RootSettings,
) {

    val binder: Flow<RootServiceClient.Connection?> = settings.useRoot.flow
        .flatMapLatest {
            if (it != true) return@flatMapLatest emptyFlow()

            callbackFlow<RootServiceClient.Connection?> {
                val resource = serviceClient.get()
                send(resource.item)
                awaitClose {
                    log(TAG) { "Closing binder resource" }
                    resource.close()
                }
            }
        }
        .catch {
            log(TAG, WARN) { "RootServiceClient.Connection was unavailable" }
            emit(null)
        }
        .setupCommonEventHandlers(TAG) { "binder" }
        .replayingShare(appScope)

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
                serviceClient.get().use { it.item.ipc.checkBase() != null }
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
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 10 * 1000,
                replayExpirationMillis = 0,
            ),
            initialValue = null
        )
        .filterNotNull()

    suspend fun isInstalled(): Boolean {
        val installed =
            KNOWN_ROOT_MANAGERS.any {
                try {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(it, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }

        log(TAG) { "isInstalled(): $installed" }
        return installed
    }

    companion object {
        internal val TAG = logTag("Root", "Manager")
        private val KNOWN_ROOT_MANAGERS = setOf(
            "com.topjohnwu.magisk"
        )
    }
}
