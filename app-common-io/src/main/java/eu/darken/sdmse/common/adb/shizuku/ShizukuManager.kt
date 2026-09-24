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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
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

    /**
     * Managers belonging to [backend]'s family (the active one if null), see
     * [ShizukuWrapper.getActiveManagerPackages].
     */
    suspend fun activeManagerIds(backend: AdbBackend? = null): Set<Pkg.Id> =
        shizukuWrapper.getActiveManagerPackages(backend).map { it.toPkgId() }.toSet()

    /**
     * An installed Shizuku-family manager that cannot serve us: an installed Porter always takes
     * priority, so while [backend] (the active one if null) is Porter and no link is held, a running
     * Shizuku is ignored.
     */
    suspend fun priorityBlockedManagerId(backend: AdbBackend? = null): Pkg.Id? {
        if ((backend ?: activeBackend()) != AdbBackend.PORTER) return null
        if (shizukuWrapper.link.first() != null) return null
        return shizukuWrapper.getActiveManagerPackage(AdbBackend.SHIZUKU)?.toPkgId()
    }

    val permissionChanges: Flow<Unit> = shizukuWrapper.permissionChanges
        .setupCommonEventHandlers(TAG) { "permissionChanges" }
        .replayingShare(appScope)

    /** The link to the manager's server, null while the user has not opted in. */
    val adbLink: Flow<AdbLink?> = settings.useShizuku.flow
        .flatMapLatest { if (it == true) shizukuWrapper.link else flowOf(null) }
        .catch { e ->
            log(TAG, WARN) { "ADB link access failed: ${e.asLog()}" }
            emit(null)
        }
        .setupCommonEventHandlers(TAG) { "link" }
        .replayingShare(appScope)

    /**
     * Is the device shizukud and we have access?
     */
    suspend fun isShizukud(): Boolean {
        val availability = availability()
        if (getManagerId(backendOf(availability)) == null) {
            log(TAG) { "isShizukud(): Shizuku is not installed" }
            return false
        }
        log(TAG, VERBOSE) { "isShizukud(): Shizuku is installed" }

        // Unknown availability does not block: the steps below find out on their own.
        if (availability is AdbAvailability.Incompatible) {
            log(TAG) { "isShizukud(): Incompatible: $availability" }
            return false
        }
        log(TAG, VERBOSE) { "isShizukud(): Not known to be incompatible" }

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

    // Reference package of [backend] (the active one if null), only used as a placeholder when nothing
    // is installed.
    suspend fun referenceManagerId(backend: AdbBackend? = null): Pkg.Id = when (backend ?: activeBackend()) {
        AdbBackend.PORTER -> PORTER_PKG_ID
        AdbBackend.SHIZUKU -> PKG_ID
    }

    /**
     * The installed manager's package for the ACTIVE backend, resolved via its permission so forks
     * and hidden-mode installs are handled, or null if no such manager is installed.
     */
    suspend fun getManagerId(backend: AdbBackend? = null): Pkg.Id? =
        shizukuWrapper.getActiveManagerPackage(backend)?.toPkgId()

    suspend fun activeBackend(): AdbBackend = shizukuWrapper.activeBackend()

    /** Null means unknown, see [ShizukuWrapper.availability]. */
    suspend fun availability(): AdbAvailability? = shizukuWrapper.availability()

    suspend fun backendOf(availability: AdbAvailability?): AdbBackend = shizukuWrapper.backendOf(availability)

    /** Diagnostics only: the UID the privileged helper runs as, 2000 when it really is shell. */
    suspend fun serverUid(): Int? = shizukuWrapper.serverUid()

    // Not cached: a stale "not installed" result would outlive installing the manager until the next
    // process restart. The lookup is cheap.
    suspend fun isInstalled(): Boolean {
        val installed = getManagerId() != null
        log(TAG) { "isInstalled(): $installed" }
        return installed
    }

    suspend fun isGranted(): Boolean? = shizukuWrapper.isGranted()

    /** Null when there is no link or the request failed. Unbounded, the caller bounds the user's wait. */
    suspend fun requestPermission(): Boolean? = shizukuWrapper.requestPermission()

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
            // Not a denial: no live link means the grant state cannot be read at all.
            null -> {
                log(TAG, VERBOSE) { "getServiceState(): No live link, grant state unknown" }
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
                adbLink.map { }.onStart { emit(Unit) },
                permissionChanges.onStart { emit(Unit) },
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
     * [AccessState.Unavailable] also covers "Shizuku not installed / not granted / incompatible".
     */
    val accessState: Flow<AccessState> = settings.useShizuku.flow
        .flatMapLatest { setting ->
            when (setting) {
                null -> flowOf(AccessState.Undecided)
                false -> flowOf(AccessState.Declined)
                true -> combine(
                    adbLink.map { }.onStart { emit(Unit) },
                    permissionChanges.onStart { emit(Unit) },
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