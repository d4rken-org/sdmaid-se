package eu.darken.sdmse.setup

import kotlinx.coroutines.flow.Flow

interface SetupModule {
    val state: Flow<State?>

    suspend fun refresh()

    interface State {
        val isComplete: Boolean
    }
}