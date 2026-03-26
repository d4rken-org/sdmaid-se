package eu.darken.sdmse.setup

import kotlinx.serialization.Serializable

@Serializable
data class SetupRoute(
    val options: SetupScreenOptions? = null,
)
