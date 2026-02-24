package eu.darken.sdmse.setup

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

suspend fun SetupModule.isComplete() = state.filterIsInstance<SetupModule.State.Current>().first().isComplete

val SetupModule.State.isComplete: Boolean
    get() = this is SetupModule.State.Current && this.isComplete
