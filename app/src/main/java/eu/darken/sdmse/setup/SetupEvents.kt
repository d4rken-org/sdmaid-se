package eu.darken.sdmse.setup

import eu.darken.sdmse.setup.saf.SAFSetupModule

sealed interface SetupEvents {
    data class SafRequestAccess(
        val item: SAFSetupModule.State.PathAccess,
    ) : SetupEvents

    data class SafWrongPathError(
        val exception: Exception
    ) : SetupEvents
}
