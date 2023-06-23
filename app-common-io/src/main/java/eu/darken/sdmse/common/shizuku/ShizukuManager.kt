package eu.darken.sdmse.common.shizuku

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.flow.shareLatest
import eu.darken.sdmse.common.shizuku.service.ShizukuServiceClient
import eu.darken.sdmse.common.shizuku.service.internal.ShizukuBaseServiceBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val settings: ShizukuSettings,
    private val dispatcherProvider: DispatcherProvider,
    private val shizukuWrapper: ShizukuWrapper,
    private val shizukuServiceClient: ShizukuServiceClient,
) {

    private var cachedState: Boolean? = null
    private val cacheLock = Mutex()

    init {
        settings.useShizuku.flow
            .mapLatest {
                log(TAG) { "Shizuku access state: $it" }
                cacheLock.withLock {
                    cachedState = null
                }
            }
            .launchIn(appScope)

        appScope.launch {
            delay(1 * 1000L)
            val currentAccess = isGranted()
            log(TAG) { "Shizuku access check: $currentAccess" }
            if (currentAccess == false) {
                log(TAG, WARN) { "Shizuku access was revoked!" }
                settings.useShizuku.value(null)
            }
        }
    }

    /**
     * Is the device shizukud and we have access?
     */
    suspend fun isShizukud(): Boolean = withContext(dispatcherProvider.IO) {
        cacheLock.withLock {
            cachedState?.let { return@withContext it }

            val newState = kotlin.run {
                if (!isInstalled()) {
                    log(TAG) { "Shizuku is not installed" }
                    return@run false
                }
                if (!isCompatible()) {
                    log(TAG) { "Shizuku version is too old" }
                    return@run false
                }
                if (isGranted() != true) {
                    log(TAG) { "Permission not granted" }
                    return@run false
                }

                try {
                    shizukuServiceClient.get().item.ipc.checkBase() != null
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Error during checkBase(): ${e.asLog()}" }
                    false
                }
            }

            newState.also { cachedState = it }
        }
    }

    /**
     * Did the user consent to SD Maid using Shizuku and is Shizuku available?
     */
    val useShizuku: Flow<Boolean> = settings.useShizuku.flow
        .mapLatest { (it ?: false) && isShizukud() }
        .shareLatest(appScope)

    val shizukuBinder: Flow<ShizukuBaseServiceBinder?> = shizukuWrapper.baseServiceBinder
        .setupCommonEventHandlers(TAG) { "binder" }
        .replayingShare(appScope)

    val permissionGrantEvents: Flow<ShizukuWrapper.ShizukuPermissionRequest> = shizukuWrapper.permissionGrantEvents
        .setupCommonEventHandlers(TAG) { "grantEvents" }
        .replayingShare(appScope)


    suspend fun isInstalled(): Boolean {
        val installed = try {
            context.packageManager.getPackageInfo(PKG, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        log(TAG) { "isInstalled(): $installed" }
        return installed
    }

    suspend fun isGranted(): Boolean? = shizukuWrapper.isGranted()

    suspend fun isCompatible(): Boolean = shizukuWrapper.isCompatible()

    suspend fun requestPermission() = shizukuWrapper.requestPermission()

    companion object {
        private val TAG = logTag("Shizuku", "Manager")
        private const val PKG = "moe.shizuku.privileged.api"
    }
}