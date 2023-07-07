package eu.darken.sdmse.common.shizuku

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
import eu.darken.sdmse.common.flow.shareLatest
import eu.darken.sdmse.common.shizuku.service.ShizukuServiceClient
import eu.darken.sdmse.common.shizuku.service.internal.ShizukuBaseServiceBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    val serviceClient: ShizukuServiceClient,
) {

    val permissionGrantEvents: Flow<ShizukuWrapper.ShizukuPermissionRequest> = shizukuWrapper.permissionGrantEvents
        .setupCommonEventHandlers(TAG) { "grantEvents" }
        .replayingShare(appScope)

    val shizukuBinder: Flow<ShizukuBaseServiceBinder?> = settings.isEnabled.flow
        .flatMapLatest { if (it == true) shizukuWrapper.baseServiceBinder else emptyFlow() }
        .setupCommonEventHandlers(TAG) { "binder" }
        .replayingShare(appScope)

    /**
     * Is the device shizukud and we have access?
     */
    suspend fun isShizukud(): Boolean = withContext(dispatcherProvider.IO) {
        if (!isInstalled()) {
            log(TAG) { "Shizuku is not installed" }
            return@withContext false
        }
        if (!isCompatible()) {
            log(TAG) { "Shizuku version is too old" }
            return@withContext false
        }

        val granted = isGranted()
        if (granted == false) {
            log(TAG) { "Permission not granted" }
            return@withContext false
        }

        if (granted == null) {
            log(TAG) { "Binder unavailable" }
            return@withContext false
        }

        isShizukuServiceAvailable().also {
            if (!it) log(TAG) { "(Our) ShizukuService is unavailable" }
        }
    }

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

    suspend fun isShizukuServiceAvailable(): Boolean = try {
        serviceClient.get().item.ipc.checkBase() != null
    } catch (e: Exception) {
        log(TAG, WARN) { "Error during checkBase(): $e" }
        false
    }

    /**
     * Did the user consent to SD Maid using Shizuku and is Shizuku available?
     */
    val useShizuku: Flow<Boolean> = settings.isEnabled.flow
        .flatMapLatest { isEnabled ->
            if (isEnabled != true) return@flatMapLatest flowOf(false)

            combine(
                shizukuBinder.map { }.onStart { emit(Unit) },
                permissionGrantEvents.map { }.onStart { emit(Unit) },
            ) { _, _ -> isShizukud() }
        }
        .shareLatest(appScope)

    companion object {
        private val TAG = logTag("Shizuku", "Manager")
        private const val PKG = "moe.shizuku.privileged.api"
    }
}