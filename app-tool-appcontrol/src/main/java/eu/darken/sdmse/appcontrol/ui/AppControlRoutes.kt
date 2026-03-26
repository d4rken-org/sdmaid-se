package eu.darken.sdmse.appcontrol.ui

import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.serialization.Serializable

@Serializable
data class AppActionRoute(
    val installId: InstallId,
)
