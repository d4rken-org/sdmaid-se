package eu.darken.sdmse.common.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Handler
import android.os.HandlerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.flow.shareLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: ShizukuSettings,
    @AppScope private val appScope: CoroutineScope,
) {

    private val handlerThread: HandlerThread by lazy {
        HandlerThread("shizuku:binder-handler")
    }
    private val handler: Handler by lazy {
        handlerThread.start()
        Handler(handlerThread.looper)
    }

    val shizukuBinder: Flow<ShizukuBinderWrapper?> = settings.useShizuku.flow
        .flatMapLatest { createShizukuBinderFlow(handler) }
        .setupCommonEventHandlers(TAG) { "binder" }
        .replayingShare(appScope)

    val permissionGrantEvents: Flow<ShizukuPermissionRequest> = createShizukuPermissionFlow(handler)
        .setupCommonEventHandlers(TAG) { "grantEvents" }
        .replayingShare(appScope)

    val useShizuku: Flow<Boolean> = settings.useShizuku.flow
        .mapLatest { (it ?: false) && isAvailable() }
        .shareLatest(appScope)

    suspend fun isGranted(): Boolean {
        val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        log(TAG) { "isGranted()=$granted" }
        return granted
    }

    suspend fun isInstalled(): Boolean {
        val installed = try {
            context.packageManager.getPackageInfo(PKG, 0)
            true
        } catch (e: NameNotFoundException) {
            false
        }
        log(TAG) { "isInstalled(): $installed" }
        return installed
    }

    suspend fun isAvailable(): Boolean {
        val available = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        log(TAG) { "isAvailable(): $available" }
        val isPreV11 = Shizuku.isPreV11()
        return available && !isPreV11
    }

    suspend fun requestPermission() {
        log(TAG) { "requestPermission()" }
        Shizuku.requestPermission(433)
    }

    companion object {
        private val TAG = logTag("Shizuku", "Manager")
        private const val PKG = "moe.shizuku.privileged.api"
    }
}