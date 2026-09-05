package eu.darken.sdmse.setup.shizuku

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.adb.shizuku.ShizukuBaseServiceBinder
import eu.darken.sdmse.common.adb.shizuku.ShizukuManager
import eu.darken.sdmse.common.adb.shizuku.ShizukuServiceState
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.coroutine.runDetachedWithTimeout
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.getLaunchIntent
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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

    /** Overridden in tests to keep the wedge case fast, never in production. */
    internal var pingTimeoutMs: Long = PING_TIMEOUT_MS

    // Last SETTLED Result, kept so re-subscription (e.g. returning to the dashboard) can emit it
    // immediately instead of regressing to Loading and flickering the setup card while the availability
    // probe re-runs (a cold AdbHost bind can take ~10s). Never holds Loading or a mid-probe state.
    @Volatile
    private var lastResult: Result? = null

    private val permissionRequester = shizukuManager.shizukuBinder
        .onEach {
            if (adbSettings.useShizuku.value() == true && shizukuManager.isGranted() == false) {
                log(TAG) { "Requesting Shizuku permission for us..." }
                shizukuManager.requestPermission()
            }
        }
        .map { }
        .onStart { emit(Unit) }

    override val state: Flow<SetupModule.State> = combine(
        refreshTrigger,
        adbSettings.useShizuku.flow,
        rootManager.useRoot,
    ) { _, useShizuku, useRoot ->
        val managerId = shizukuManager.getManagerId()
        // The card's open action launches this package. The detected manager can be Shizuku+'s Compat Hub,
        // which has no launcher activity, so prefer the first manager app that can actually be opened.
        val openable = managerId?.let {
            withContext(dispatcherProvider.IO) {
                shizukuManager.managerIds().firstOrNull { pkg -> pkg.getLaunchIntent(context) != null }
            }
        }
        val baseState = Result(
            pkg = openable ?: managerId ?: shizukuManager.shizukuPkgId,
            useShizuku = useShizuku,
            isInstalled = managerId != null,
            isCompatible = shizukuManager.isCompatible(),
            alsoHasRoot = useRoot,
        )

        if (useShizuku != true) return@combine flowOf<SetupModule.State>(baseState)

        combine(
            // Just tie the lifecycle of the requester to the state's subscribers
            permissionRequester,
            shizukuManager.permissionGrantEvents.map { }.onStart { emit(Unit) },
            shizukuManager.shizukuBinder.onStart { emit(null) },
        ) { _, _, binder -> binder }
            // transformLatest, not map: the probe below has to announce itself BEFORE it runs. A cold
            // bind can take the full ADB connect budget, and without a state saying so the card kept
            // offering a retry button that silently did nothing for those seconds.
            .transformLatest<ShizukuBaseServiceBinder?, SetupModule.State> { binder ->
                emit(
                    baseState.copy(
                        // Keep showing what we last knew rather than regressing to NotChecked, so a
                        // retry doesn't blank out the failure message it was triggered from.
                        serviceState = lastResult?.serviceState ?: ShizukuServiceState.NotChecked,
                        isChecking = true,
                    )
                )

                val settled = try {
                    // pingBinder() is a synchronous PING_TRANSACTION: against a Shizuku server that is
                    // alive but not servicing requests it never returns, and an unbounded wedge here
                    // stalls this flow so the card stays on Loading forever - the exact symptom the
                    // ADB-side timeouts guard against. Detached + bounded, same trade as isGranted().
                    val basicService = binder?.let { b ->
                        appScope.runDetachedWithTimeout(dispatcherProvider.IO, pingTimeoutMs) { b.pingBinder() }
                            ?: false.also { log(TAG) { "pingBinder() did not respond within ${pingTimeoutMs}ms" } }
                    } ?: false

                    baseState.copy(
                        basicService = basicService,
                        serviceState = shizukuManager.getServiceState(),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Must settle rather than propagate. Anything thrown after the checking state
                    // above kills the sharing coroutine with that state left in replayingShare's
                    // replay slot, and since the coroutine is dead no refresh can ever replace it:
                    // every later subscriber would see a permanently disabled retry button.
                    // runDetachedWithTimeout propagates whatever its block throws, and pingBinder()
                    // is a binder call, so this is reachable (e.g. DeadObjectException).
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

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = rngString
    }

    suspend fun toggleUseShizuku(useShizuku: Boolean?) {
        log(TAG) { "toggleUseShizuku(useShizuku=$useShizuku)" }
        // Drop any cached state so we don't replay a stale Result for the previous setting.
        lastResult = null
        val couldUseShizuku = shizukuManager.useShizuku.first()
        if (useShizuku == true && shizukuManager.isGranted() == false) {
            val grantResult = coroutineScope {
                val eventResult = async {
                    shizukuManager.permissionGrantEvents
                        .mapLatest { shizukuManager.isGranted() }
                        .first()
                }

                log(TAG) { "Requesting permission" }
                shizukuManager.requestPermission()

                withTimeoutOrNull(30 * 1000) { eventResult.await() }
            }

            log(TAG) { "Permission grant result was $grantResult" }
            adbSettings.useShizuku.value(grantResult.takeIf { it == true })
        } else {
            adbSettings.useShizuku.value(useShizuku)
        }

        if (!couldUseShizuku && useShizuku == true) {
            // TODO find a smarter way to do this, i.e. by waiting for a specific event.
            // Small delay to allow Shizuku service to bind
            delay(1500)
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
        val isCompatible: Boolean = false,
        val isInstalled: Boolean = false,
        val basicService: Boolean = false,
        val serviceState: ShizukuServiceState = ShizukuServiceState.NotChecked,
        /** A probe is running right now. Only gates the retry affordance, never the message. */
        val isChecking: Boolean = false,
        val alsoHasRoot: Boolean = false,
    ) : SetupModule.State.Current {

        /** Derived, not stored: one source of truth, so it can't disagree with [serviceState]. */
        val ourService: Boolean
            get() = serviceState is ShizukuServiceState.Available

        override val type: SetupModule.Type = SetupModule.Type.SHIZUKU

        // "Wants Shizuku but it isn't installed" is NOT complete. Treating it as complete hid the card
        // and rendered the whole setup screen as done, so users believed Shizuku was working while we
        // silently fell back to the accessibility service.
        override val isComplete: Boolean =
            useShizuku == false || !isCompatible || (useShizuku == true && isInstalled && ourService)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ShizukuSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "ADB", "Shizuku", "Module")

        // Generous on purpose: a false timeout would report a working Shizuku as unavailable, which is
        // worse than waiting. This only has to turn "never" into "eventually".
        internal const val PING_TIMEOUT_MS = 15 * 1000L
    }
}