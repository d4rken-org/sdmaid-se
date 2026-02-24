package eu.darken.sdmse.setup

import kotlinx.coroutines.flow.Flow

interface SetupHealerSource {

    val state: Flow<State>

    data class State(
        val healAttemptCount: Int = 0,
        val isWorking: Boolean = false,
    )
}
