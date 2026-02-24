package eu.darken.sdmse.setup

import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

suspend fun SetupModule.isComplete() = state.filterIsInstance<SetupModule.State.Current>().first().isComplete

val SetupModule.State.isComplete: Boolean
    get() = this is SetupModule.State.Current && this.isComplete

/**
 * Pluggable handler for showing setup hints from tool modules.
 * Registered by the app module at startup.
 */
var showSetupHint: ((Fragment, Set<SetupModule.Type>) -> Unit)? = null
