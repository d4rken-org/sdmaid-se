package eu.darken.sdmse.setup

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetupManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val setupModules: Set<@JvmSuppressWildcards SetupModule>
) {
    data class SetupState(
        val moduleStates: List<SetupModule.State>
    ) {

        val isComplete: Boolean = moduleStates.all { it.isComplete }
    }

    val state: Flow<SetupState> = combine(setupModules.map { it.state }) { moduleStates ->
        SetupState(moduleStates = moduleStates.filterNotNull().toList())
    }
        .onEach { log(TAG) { "Setup state: $it" } }
        .replayingShare(appScope)


    suspend fun refresh() {
        log(TAG) { "refresh()" }
        setupModules.forEach { it.refresh() }
    }

    companion object {
        private val TAG = logTag("Setup", "Manager")
    }
}