package eu.darken.sdmse.common.adb.shizuku

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.access.AccessState
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.adb.service.AdbServiceClient
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    settings: AdbSettings,
    private val shizukuWrapper: ShizukuWrapper,
    val serviceClient: AdbServiceClient,
) {

    suspend fun managerIds() = setOf(PKG_ID)

    val permissionGrantEvents: Flow<ShizukuWrapper.ShizukuPermissionRequest> = shizukuWrapper.permissionGrantEvents
        .setupCommonEventHandlers(TAG) { "grantEvents" }
        .replayingShare(appScope)

    val shizukuBinder: Flow<ShizukuBaseServiceBinder?> = settings.useShizuku.flow
        // Only touch the Shizuku binder if the user opted in AND Shizuku is actually installed.
        // Otherwise (e.g. useShizuku left enabled after uninstalling Shizuku) every subscription would
        // probe the absent service and spam "binder haven't been received" on each resume.
        .flatMapLatest { if (it == true && isInstalled()) shizukuWrapper.baseServiceBinder else flowOf(null) }
        .catch { e ->
            log(TAG, WARN) { "Shizuku binder access failed: ${e.asLog()}" }
            emit(null)
        }
        .setupCommonEventHandlers(TAG) { "binder" }
        .replayingShare(appScope)

    /**
     * Is the device shizukud and we have access?
     */
    suspend fun isShizukud(): Boolean {
        if (!isInstalled()) {
            log(TAG) { "isShizukud(): Shizuku is not installed" }
            return false
        }
        log(TAG, VERBOSE) { "isShizukud(): Shizuku is installed" }

        if (!isCompatible()) {
            log(TAG) { "isShizukud(): Shizuku version is too old" }
            return false
        }
        log(TAG, VERBOSE) { "isShizukud(): Shizuku is recent enough" }

        val granted = isGranted()
        if (granted == false) {
            log(TAG) { "isShizukud(): Permission not granted" }
            return false
        }
        log(TAG, VERBOSE) { "isShizukud(): Permission is granted" }

        if (granted == null) {
            log(TAG) { "isShizukud(): Binder unavailable" }
            return false
        }
        log(TAG, VERBOSE) { "isShizukud(): Binder available" }

        log(TAG, VERBOSE) { "isShizukud(): Checking availability of (Our) ShizukuService..." }
        return isOurServiceAvailable().also {
            if (it) log(TAG, VERBOSE) { "isShizukud(): (Our) ShizukuService is available :)" }
            else log(TAG) { "isShizukud(): (Our) ShizukuService is unavailable" }
        }
    }

    val shizukuPkgId: Pkg.Id
        get() = PKG_ID

    // Not cached: a stale "not installed" result would keep the binder gate (see shizukuBinder) closed
    // even after Shizuku gets installed, until the next process restart. The package lookup is cheap.
    suspend fun isInstalled(): Boolean {
        val installed = try {
            context.packageManager.getPackageInfo(PKG_ID.name, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        log(TAG) { "isInstalled(): $installed" }
        return installed
    }

    suspend fun isGranted(): Boolean? = shizukuWrapper.isGranted()

    private var isCompatibleCache: Boolean? = null
    private val isCompatibleLock = Mutex()

    suspend fun isCompatible(): Boolean = isCompatibleLock.withLock {
        isCompatibleCache?.let { return@withLock it }

        shizukuWrapper.isCompatible().also {
            log(TAG) { "isCompatible(): $it" }
            isCompatibleCache = it
        }
    }

    suspend fun requestPermission() = shizukuWrapper.requestPermission()


    suspend fun isOurServiceAvailable(): Boolean = withContext(dispatcherProvider.IO) {
        if (isGranted() != true) {
            log(TAG, VERBOSE) { "isOurServiceAvailable(): Shizuku permission not granted" }
            return@withContext false
        }
        try {
            log(TAG, VERBOSE) { "isOurServiceAvailable(): Requesting service client" }
            serviceClient.get().use { it.item.ipc.checkBase() != null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "isOurServiceAvailable(): Error during checkBase(): $e" }
            false
        }
    }

    /**
     * Did the user consent to SD Maid using Shizuku and is Shizuku available?
     */
    val useShizuku: Flow<Boolean> = settings.useShizuku.flow
        .flatMapLatest { isEnabled ->
            if (isEnabled != true) return@flatMapLatest flowOf(false)

            combine(
                shizukuBinder.map { }.onStart { emit(Unit) },
                permissionGrantEvents.map { }.onStart { emit(Unit) },
            ) { _, _ -> isShizukud() }
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 10 * 1000,
                replayExpirationMillis = 0,
            ),
            initialValue = null
        )
        .filterNotNull()

    /**
     * Probe-aware status for UI gating. Mirrors [useShizuku] but exposes the distinct
     * decided/checking/active/unavailable/declined states the gate UI needs.
     * [AccessState.Unavailable] also covers "Shizuku not installed / not granted / too old".
     */
    val accessState: Flow<AccessState> = settings.useShizuku.flow
        .flatMapLatest { setting ->
            when (setting) {
                null -> flowOf(AccessState.Undecided)
                false -> flowOf(AccessState.Declined)
                true -> combine(
                    shizukuBinder.map { }.onStart { emit(Unit) },
                    permissionGrantEvents.map { }.onStart { emit(Unit) },
                ) { _, _ -> if (isShizukud()) AccessState.Active else AccessState.Unavailable }
                    .onStart { emit(AccessState.Checking) }
            }
        }
        .setupCommonEventHandlers(TAG) { "accessState" }
        .replayingShare(appScope)

    companion object {
        private val TAG = logTag("ADB", "Shizuku", "Manager")
        internal val PKG_ID = "moe.shizuku.privileged.api".toPkgId()
    }
}