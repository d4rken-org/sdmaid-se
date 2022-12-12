package eu.darken.sdmse.setup

import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.randomString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetupManager @Inject constructor(
    private val setupModules: Set<@JvmSuppressWildcards SetupModule>
) {
    data class SetupState(
        val moduleStates: List<SetupModule.State>
    ) {

        val isComplete: Boolean = moduleStates.all { it.isComplete }
    }

    private val refreshTrigger = MutableStateFlow(randomString())
    val state: Flow<SetupState> = refreshTrigger
        .map { _ ->
            setupModules.map { it.determineState() }
        }
        .map { SetupState(moduleStates = it) }


    fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = randomString()
    }

    companion object {
        private val TAG = logTag("Setup", "Manager")
    }
}