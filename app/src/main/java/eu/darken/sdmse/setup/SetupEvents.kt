package eu.darken.sdmse.setup

import eu.darken.sdmse.setup.saf.SAFSetupModule

sealed interface SetupEvents {
    data class RequestSafAccess(
        val item: SAFSetupModule.State.PathAccess,
    ) : SetupEvents
}
