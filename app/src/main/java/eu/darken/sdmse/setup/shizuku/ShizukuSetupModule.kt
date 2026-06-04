package eu.darken.sdmse.setup.shizuku

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.adb.shizuku.ShizukuManager
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.setup.SetupModule
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
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuSetupModule @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val adbSettings: AdbSettings,
    private val shizukuManager: ShizukuManager,
    private val dataAreaManager: DataAreaManager,
    rootManager: RootManager,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)

    // Last known concrete Result, kept so re-subscription (e.g. returning to the dashboard) can emit it
    // immediately instead of regressing to Loading and flickering the setup card while the availability
    // probe re-runs (a cold AdbHost bind can take ~10s). Only ever holds a real Result, never Loading.
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
        val baseState = Result(
            pkg = shizukuManager.shizukuPkgId,
            useShizuku = useShizuku,
            isInstalled = shizukuManager.isInstalled(),
            isCompatible = shizukuManager.isCompatible(),
            alsoHasRoot = useRoot,
        )

        if (useShizuku != true) return@combine flowOf(baseState)

        combine(
            // Just tie the lifecycle of the requester to the state's subscribers
            permissionRequester,
            shizukuManager.permissionGrantEvents.map { }.onStart { emit(Unit) },
            shizukuManager.shizukuBinder.onStart { emit(null) },
        ) { _, _, binder ->
            @Suppress("USELESS_CAST")
            baseState.copy(
                basicService = binder?.pingBinder() ?: false,
                ourService = shizukuManager.isOurServiceAvailable(),
            ) as SetupModule.State
        }
    }
        .flatMapLatest { it }
        .onEach { if (it is Result) lastResult = it }
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
        val ourService: Boolean = false,
        val alsoHasRoot: Boolean = false,
    ) : SetupModule.State.Current {

        override val type: SetupModule.Type = SetupModule.Type.SHIZUKU

        override val isComplete: Boolean =
            useShizuku == false || !isCompatible || (useShizuku == true && (!isInstalled || ourService))
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ShizukuSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "ADB", "Shizuku", "Module")
    }
}