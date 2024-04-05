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
    override val state: Flow<SetupModule.State> = combine(refreshTrigger, rootSettings.useRoot.flow) { _, useRoot ->
        val baseState = Result(
            useRoot = useRoot,
            isInstalled = rootManager.isInstalled(),
        )

        if (useRoot != true) return@combine flowOf(baseState)

        rootManager.binder
            .onStart { emit(null) }
            .map { connection ->
                if (connection == null) return@map baseState

                @Suppress("USELESS_CAST")
                baseState.copy(
                    ourService = try {
                        connection.ipc.checkBase() != null
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Error while checking for root: $e" }
                        false
                    },
                ) as SetupModule.State
            }
    }
        .flatMapLatest { it }
        .onStart { emit(Loading()) }
        .onEach { log(TAG) { "New Root setup state: $it" } }
        .replayingShare(appScope)

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = rngString
    }

    suspend fun toggleUseRoot(useRoot: Boolean?) {
        log(TAG) { "toggleUseRoot(useRoot=$useRoot)" }
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

        override val isComplete: Boolean = useRoot != null
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: RootSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Root", "Module")
    }
}