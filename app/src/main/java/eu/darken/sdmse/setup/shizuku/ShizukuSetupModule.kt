package eu.darken.sdmse.setup.shizuku

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.adb.shizuku.AdbAvailability
import eu.darken.sdmse.common.adb.shizuku.AdbBackend
import eu.darken.sdmse.common.adb.shizuku.ShizukuManager
import eu.darken.sdmse.common.adb.shizuku.ShizukuServiceState
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.getLaunchIntent
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuSetupModule @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val adbSettings: AdbSettings,
    private val shizukuManager: ShizukuManager,
    private val dataAreaManager: DataAreaManager,
    rootManager: RootManager,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)

    // Last SETTLED Result, kept so re-subscription (e.g. returning to the dashboard) can emit it
    // immediately instead of regressing to Loading and flickering the setup card while the availability
    // probe re-runs (a cold AdbHost bind can take ~10s). Never holds Loading or a mid-probe state.
    @Volatile
    private var lastResult: Result? = null

    private val permissionRequestLock = Any()
    private var permissionRequest: Deferred<Boolean?>? = null

    // The request suspends until the user answers the manager's prompt, so it runs detached and is
    // shared: a second caller joins the prompt already on screen instead of stacking another one.
    private fun requestPermissionOnce(): Deferred<Boolean?> = synchronized(permissionRequestLock) {
        permissionRequest?.takeIf { it.isActive } ?: appScope
            .async {
                log(TAG) { "Requesting ADB permission..." }
                shizukuManager.requestPermission().also { log(TAG) { "ADB permission request result: $it" } }
            }
            .also { permissionRequest = it }
    }

    private val permissionRequester: Flow<Unit> = shizukuManager.adbLink
        .onEach { link ->
            if (link != null && adbSettings.useShizuku.value() == true && shizukuManager.isGranted() == false) {
                requestPermissionOnce()
            }
        }
        // Only the lifecycle matters: a new link already restarts the whole state through the outer combine.
        .map { }
        .onStart { emit(Unit) }
        .distinctUntilChanged()

    override val state: Flow<SetupModule.State> = combine(
        refreshTrigger,
        adbSettings.useShizuku.flow,
        rootManager.useRoot,
        // A link attaching can lift the priority block below, e.g. Porter being started.
        shizukuManager.adbLink.onStart { emit(null) }.distinctUntilChanged(),
    ) { _, useShizuku, useRoot, _ ->
        // One snapshot per emission, so backend, manager and hints can't disagree with each other.
        val availability = shizukuManager.availability()
        val backend = shizukuManager.backendOf(availability)
        val managerId = shizukuManager.getManagerId(backend)
        // The card's open action launches this package. The detected manager can be Shizuku+'s Compat Hub,
        // which has no launcher activity, so prefer the first manager app that can actually be opened.
        // Stays inside the active backend's family: the other manager can't affect the link we wait on.
        val openable = managerId?.let {
            withContext(dispatcherProvider.IO) {
                shizukuManager.activeManagerIds(backend).firstOrNull { pkg -> pkg.getLaunchIntent(context) != null }
            }
        }
        val blockedManager = shizukuManager.priorityBlockedManagerId(backend)
        val incompatible = availability as? AdbAvailability.Incompatible
        val pkg = openable ?: managerId ?: shizukuManager.referenceManagerId(backend)
        val baseState = Result(
            pkg = pkg,
            useShizuku = useShizuku,
            isInstalled = managerId != null,
            alsoHasRoot = useRoot,
            backend = backend,
            managerLabel = if (managerId != null) labelOf(pkg) else null,
            blockedManager = blockedManager,
            blockedManagerLabel = labelOf(blockedManager),
            managerTooOld = incompatible?.serverTooOld == true,
            sdMaidTooOld = incompatible?.clientTooOld == true,
        )

        if (useShizuku != true) return@combine flowOf<SetupModule.State>(baseState)

        combine(
            // Just tie the lifecycle of the requester to the state's subscribers
            permissionRequester,
            shizukuManager.permissionChanges.onStart { emit(Unit) },
        ) { _, _ -> }
            // transformLatest, not map: the probe below has to announce itself BEFORE it runs. A cold
            // bind can take the full ADB connect budget, and without a state saying so the card kept
            // offering a retry button that silently did nothing for those seconds.
            .transformLatest<Unit, SetupModule.State> {
                emit(
                    baseState.copy(
                        // Keep showing what we last knew rather than regressing to NotChecked, so a
                        // retry doesn't blank out the failure message it was triggered from.
                        serviceState = lastResult?.serviceState ?: ShizukuServiceState.NotChecked,
                        isChecking = true,
                    )
                )

                val settled = try {
                    baseState.copy(serviceState = shizukuManager.getServiceState())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Must settle rather than propagate. Anything thrown after the checking state
                    // above kills the sharing coroutine with that state left in replayingShare's
                    // replay slot, and since the coroutine is dead no refresh can ever replace it:
                    // every later subscriber would see a permanently disabled retry button.
                    log(TAG, WARN) { "Shizuku probe failed: ${e.asLog()}" }
                    baseState.copy(serviceState = ShizukuServiceState.Failed)
                }

                emit(settled)
            }
    }
        .flatMapLatest { it }
        // Only settled results: caching a mid-probe state would let onStart replay isChecking=true
        // with no probe behind it, leaving the retry button disabled forever.
        .onEach { if (it is Result && !it.isChecking) lastResult = it }
        .onStart {
            // Don't regress to Loading if we already know the result: emit the last known state so the
            // dashboard setup card doesn't flicker while the probe re-runs in the background. Guard
            // against a useShizuku change that happened while we had no subscribers.
            val cached = lastResult
            if (cached != null && cached.useShizuku == adbSettings.useShizuku.value()) {
                emit(cached)
            } else {
                emit(Loading())
            }
        }
        .onEach { log(TAG) { "New Shizuku setup state: $it" } }
        .replayingShare(appScope)

    // Runs outside the probe's catch below, so it must not throw: getLabel2() only converts
    // NameNotFoundException, and anything else (e.g. a PackageManager binder death) would kill the
    // sharing coroutine, leaving every later subscriber stuck on the state it died in.
    // Blank is treated as absent so the card can't render "... through .".
    private suspend fun labelOf(pkgId: Pkg.Id?): String? = pkgId?.let {
        withContext(dispatcherProvider.IO) {
            try {
                context.packageManager.getLabel2(it)?.takeIf { label -> label.isNotBlank() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "labelOf($it) failed: ${e.asLog()}" }
                null
            }
        }
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = rngString
    }

    suspend fun toggleUseShizuku(useShizuku: Boolean?) {
        log(TAG) { "toggleUseShizuku(useShizuku=$useShizuku)" }
        // Drop any cached state so we don't replay a stale Result for the previous setting.
        lastResult = null
        val couldUseShizuku = shizukuManager.useShizuku.first()
        val newValue = if (useShizuku == true && shizukuManager.isGranted() == false) {
            val grantResult = withTimeoutOrNull(30 * 1000) { requestPermissionOnce().await() }
            log(TAG) { "Permission grant result was $grantResult" }
            grantResult.takeIf { it == true }
        } else {
            useShizuku
        }
        adbSettings.useShizuku.value(newValue)

        if (!couldUseShizuku && newValue == true) {
            // Wait for the link to actually attach rather than guessing at how long it takes.
            withTimeoutOrNull(SERVICE_BIND_TIMEOUT_MS) {
                shizukuManager.adbLink.filterNotNull().first()
            } ?: log(TAG, WARN) { "Service did not bind within ${SERVICE_BIND_TIMEOUT_MS}ms" }
        }

        dataAreaManager.reload()
    }

    data class Loading(
        override val startAt: Instant = Instant.now(),
    ) : SetupModule.State.Loading {
        override val type: SetupModule.Type = SetupModule.Type.SHIZUKU
    }

    data class Result(
        val pkg: Pkg.Id,
        val useShizuku: Boolean?,
        val isInstalled: Boolean = false,
        val serviceState: ShizukuServiceState = ShizukuServiceState.NotChecked,
        /** A probe is running right now. Only gates the retry affordance, never the message. */
        val isChecking: Boolean = false,
        val alsoHasRoot: Boolean = false,
        val backend: AdbBackend = AdbBackend.SHIZUKU,
        /**
         * What [pkg] calls itself, so a renamed fork is named correctly instead of "Shizuku".
         * Null when nothing is installed or the label could not be read; callers fall back to
         * [backend]'s own label.
         */
        val managerLabel: String? = null,
        /**
         * An installed Shizuku-family manager we can't use because an installed Porter takes priority
         * and isn't connected. Null in every other case.
         */
        val blockedManager: Pkg.Id? = null,
        /** Same as [managerLabel], for [blockedManager]. */
        val blockedManagerLabel: String? = null,
        /** The manager's server is too old to talk to this version of SD Maid. */
        val managerTooOld: Boolean = false,
        /** This version of SD Maid is too old to talk to the manager's server. */
        val sdMaidTooOld: Boolean = false,
    ) : SetupModule.State.Current {

        /** Derived, not stored: one source of truth, so it can't disagree with [serviceState]. */
        val ourService: Boolean
            get() = serviceState is ShizukuServiceState.Available

        override val type: SetupModule.Type = SetupModule.Type.SHIZUKU

        // "Wants Shizuku but it isn't installed" is NOT complete. Treating it as complete hid the card
        // and rendered the whole setup screen as done, so users believed Shizuku was working while we
        // silently fell back to the accessibility service. An incompatible manager isn't complete
        // either: the card has to say which side needs an update.
        override val isComplete: Boolean =
            useShizuku == false || (useShizuku == true && isInstalled && ourService)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ShizukuSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "ADB", "Shizuku", "Module")

        // Ceiling for the post-grant wait, not an expected duration: the wait ends as soon as the
        // link attaches. Expiring only means the card reports "waiting" a moment longer.
        internal const val SERVICE_BIND_TIMEOUT_MS = 10 * 1000L
    }
}
