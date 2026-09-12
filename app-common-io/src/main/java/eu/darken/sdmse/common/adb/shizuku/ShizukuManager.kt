package eu.darken.sdmse.common.adb.shizuku

import eu.darken.sdmse.common.access.AccessState
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.adb.isAdbConnectTimeout
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
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    settings: AdbSettings,
    private val shizukuWrapper: ShizukuWrapper,
    val serviceClient: AdbServiceClient,
) {

    // Both reference packages plus every installed app that defines a manager permission of either
    // family (a renamed fork, Shizuku+ next to its Compat Hub, Porter).
    // Consumers (e.g. AppCleaner) use this as a set-membership test to recognize a manager app, so
    // the reference packages are included whether or not they are installed - same as before.
    suspend fun managerIds(): Set<Pkg.Id> =
        setOf(PKG_ID, PORTER_PKG_ID) + shizukuWrapper.getManagerPackages().map { it.toPkgId() }

    /** Managers belonging to the active backend's family, see [ShizukuWrapper.getActiveManagerPackages]. */
    suspend fun activeManagerIds(): Set<Pkg.Id> =
        shizukuWrapper.getActiveManagerPackages().map { it.toPkgId() }.toSet()

    /**
     * An installed manager that belongs to the OTHER family, i.e. one we cannot talk to this process.
     *
     * The backend latches at provider init, so a manager installed afterwards can be invisible to
     * [getManagerId] until the app is fully restarted. This is what lets the UI say so.
     */
    suspend fun inactiveFamilyManagerId(): Pkg.Id? {
        val active = shizukuWrapper.getActiveManagerPackages().toSet()
        return shizukuWrapper.getManagerPackages().firstOrNull { it !in active }?.toPkgId()
    }

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

    // Reference package of the active backend, only used as a placeholder when nothing is installed.
    suspend fun referenceManagerId(): Pkg.Id = when (activeBackend()) {
        AdbBackend.PORTER -> PORTER_PKG_ID
        AdbBackend.SHIZUKU -> PKG_ID
    }

    /**
     * The installed manager's package for the ACTIVE backend, resolved via its permission so forks
     * and hidden-mode installs are handled, or null if no such manager is installed.
     */
    suspend fun getManagerId(): Pkg.Id? = shizukuWrapper.getActiveManagerPackage()?.toPkgId()

    suspend fun activeBackend(): AdbBackend = shizukuWrapper.activeBackend()

    /** Diagnostics only: the UID the privileged helper runs as, 2000 when it really is shell. */
    suspend fun serverUid(): Int? = shizukuWrapper.serverUid()

    // Not cached: a stale "not installed" result would keep the binder gate (see shizukuBinder) closed
    // even after Shizuku gets installed, until the next process restart. The lookup is cheap.
    suspend fun isInstalled(): Boolean {
        val installed = getManagerId() != null
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


    suspend fun isOurServiceAvailable(): Boolean = getServiceState() is ShizukuServiceState.Available

    /**
     * Same probe as [isOurServiceAvailable], but says WHY when the answer is no.
     *
     * The distinction that matters for the UI is "we have not finished looking" versus "we looked
     * and it will not work": only the latter is worth telling the user about, and only the latter
     * should offer a retry.
     */
    suspend fun getServiceState(): ShizukuServiceState = withContext(dispatcherProvider.IO) {
        when (isGranted()) {
            false -> {
                log(TAG, VERBOSE) { "getServiceState(): Shizuku permission not granted" }
                return@withContext ShizukuServiceState.PermissionDenied
            }
            // Not a denial: no live binder means the grant state cannot be read at all.
            null -> {
                log(TAG, VERBOSE) { "getServiceState(): No live binder, grant state unknown" }
                return@withContext ShizukuServiceState.Unknown
            }

            true -> {}
        }
        try {
            log(TAG, VERBOSE) { "getServiceState(): Requesting service client" }
            val alive = serviceClient.get().use { it.item.ipc.checkBase() != null }
            if (alive) {
                ShizukuServiceState.Available
            } else {
                // Connected, but the host handed back nothing usable.
                log(TAG, WARN) { "getServiceState(): checkBase() returned null" }
                ShizukuServiceState.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "getServiceState(): Error during checkBase(): ${e.asLog()}" }
            // A spent connect budget is the signature of Shizuku's user service never calling back,
            // but the same defect also surfaces as a handshake failure, so both are terminal.
            if (e.isAdbConnectTimeout()) ShizukuServiceState.TimedOut else ShizukuServiceState.Failed
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
        internal val PORTER_PKG_ID = "eu.darken.porter".toPkgId()
    }
}