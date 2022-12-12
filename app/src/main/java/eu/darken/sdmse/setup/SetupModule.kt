package eu.darken.sdmse.setup

interface SetupModule {
    suspend fun determineState(): State

    interface State {
        val isComplete: Boolean
    }
}