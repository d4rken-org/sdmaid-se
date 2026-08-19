package eu.darken.sdmse.setup.root

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.access.AccessState
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.RootSettings
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
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

    // Last known concrete Result, kept so re-subscription (e.g. returning to the dashboard) can emit it
    // immediately instead of regressing to Loading and flickering the setup card while the availability
    // probe re-runs (acquiring the root host can cold-bind a su session). Only ever holds a real Result.
    @Volatile
    private var lastResult: Result? = null

    // Dedicated transition memory. Deliberately NOT lastResult, which gets cleared on
    // toggle/refresh for the replay logic; clearing it would suppress the very transition
    // this needs to see.
    @Volatile
    private var lastActive: Boolean? = null

    override val state: Flow<SetupModule.State> = rootManager.accessState
        .map { access ->
            if (access is AccessState.Checking) {
                Loading()
            } else {
                Result(
                    useRoot = access.toUseRoot(),
                    isInstalled = rootManager.isInstalled(),
                    ourService = access is AccessState.Active,
                )
            }
        }
        .onEach { state ->
            if (state !is Result) return@onEach
            lastResult = state
            val isActive = state.useRoot == true && state.ourService
            val was = lastActive
            lastActive = isActive
            // Root changes which storage areas are detectable. Reload only on a real change, and
            // never on the first observation, where nothing has changed yet.
            if (was != null && was != isActive) dataAreaManager.reload()
        }
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
        // Signal only: SetupManager.refresh() runs the modules sequentially, so awaiting a root
        // connect here would stall every module behind us.
        lastResult = null
        rootManager.refresh()
    }

    suspend fun toggleUseRoot(useRoot: Boolean?) {
        log(TAG) { "toggleUseRoot(useRoot=$useRoot)" }
        // Drop any cached state so we don't replay a stale Result for the previous setting.
        lastResult = null
        // No explicit refresh: the setting is part of the probe key, so accessState re-evaluates.
        rootSettings.useRoot.value(useRoot)
    }

    private fun AccessState.toUseRoot(): Boolean? = when (this) {
        AccessState.Undecided -> null
        AccessState.Declined -> false
        else -> true
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

        override val isComplete: Boolean = when (useRoot) {
            null -> false
            false -> true
            true -> !isInstalled || ourService
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: RootSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Root", "Module")
    }
}
