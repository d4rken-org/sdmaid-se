package eu.darken.sdmse.common.shizuku

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.shizuku.service.internal.ShizukuBaseServiceBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val settings: ShizukuSettings,
    private val shizukuWrapper: ShizukuWrapper,
) {

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

    suspend fun isGranted(): Boolean = shizukuWrapper.isGranted()

    suspend fun isCompatible(): Boolean = shizukuWrapper.isCompatible()

    suspend fun requestPermission() = shizukuWrapper.requestPermission()

    companion object {
        private val TAG = logTag("Shizuku", "Manager")
        private const val PKG = "moe.shizuku.privileged.api"
    }
}