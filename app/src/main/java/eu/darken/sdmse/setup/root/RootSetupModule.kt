package eu.darken.sdmse.setup.root

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.RootSettings
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootSetupModule @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val rootSettings: RootSettings,
    private val rootManager: RootManager,
    private val dataAreaManager: DataAreaManager,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)

    // Last known concrete Result, kept so re-subscription (e.g. returning to the dashboard) can emit it
    // immediately instead of regressing to Loading and flickering the setup card while the availability
    // probe re-runs (acquiring the root host can cold-bind a su session). Only ever holds a real Result.
    @Volatile
    private var lastResult: Result? = null

    override val state: Flow<SetupModule.State> = combine(refreshTrigger, rootSettings.useRoot.flow) { _, useRoot ->
        val baseState = Result(
            useRoot = useRoot,
            isInstalled = rootManager.isInstalled(),
        )

        if (useRoot != true) return@combine flowOf(baseState)

        rootManager.binder
            .map { connection ->
                if (connection == null) return@map baseState

                @Suppress("USELESS_CAST")
                baseState.copy(
                    ourService = try {
                        connection.ipc.checkBase() != null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Error while checking for root: $e" }
                        false
                    },
                ) as SetupModule.State
            }
            // Stay in Loading while the availability probe cold-binds the su session, instead of emitting
            // a settled incomplete Result. A synthetic null here used to map to baseState (ourService=false),
            // briefly flagging setup as incomplete and flashing the dashboard setup card on every launch.
            // A genuine acquisition failure still surfaces: binder emits null via its catch block (real
            // failure), which is mapped to baseState above.
            .onStart { emit(Loading()) }
    }
        .flatMapLatest { it }
        .onEach { if (it is Result) lastResult = it }
        .onStart {
            // Don't regress to Loading if we already know the result: emit the last known state so the
            // dashboard setup card doesn't flicker while the probe re-runs. Guard against a useRoot
            // change that happened while we had no subscribers.
            val cached = lastResult
            if (cached != null && cached.useRoot == rootSettings.useRoot.value()) {
                emit(cached)
            } else {
                emit(Loading())
            }
        }
        .onEach { log(TAG) { "New Root setup state: $it" } }
        .replayingShare(appScope)

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = rngString
    }

    suspend fun toggleUseRoot(useRoot: Boolean?) {
        log(TAG) { "toggleUseRoot(useRoot=$useRoot)" }
        // Drop any cached state so we don't replay a stale Result for the previous setting.
        lastResult = null
        rootSettings.useRoot.value(useRoot)

        if (useRoot == true) {
            // If we just allowed root, wait until the user has confirmed the pop-up
            rootManager.useRoot
                .filter { it }
                .take(1)
                .onEach { dataAreaManager.reload() }
                .launchIn(appScope)
        } else {
            dataAreaManager.reload()
        }
    }

    data class Loading(
        override val startAt: Instant = Instant.now(),
    ) : SetupModule.State.Loading {
        override val type: SetupModule.Type = SetupModule.Type.ROOT
    }

    data class Result(
        val useRoot: Boolean?,
        val isInstalled: Boolean = false,
        val ourService: Boolean = false,
    ) : SetupModule.State.Current {

        override val type: SetupModule.Type = SetupModule.Type.ROOT

        override val isComplete: Boolean =
            useRoot == false || (useRoot == true && (!isInstalled || ourService))
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: RootSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Root", "Module")
    }
}