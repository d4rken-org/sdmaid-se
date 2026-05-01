package eu.darken.sdmse.corpsefinder.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.serialization.APathSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

@Serializable
data object CorpseFinderSettingsRoute : NavigationDestination

@Serializable
data object CorpseFinderListRoute : NavigationDestination

@Serializable
data class CorpseDetailsRoute(
    val corpsePathJson: String? = null,
) : NavigationDestination {
    constructor(corpsePath: APath?) : this(
        corpsePathJson = corpsePath?.let { Json.encodeToString(APathSerializer, it) },
    )

    @Transient
    val corpsePath: APath? = corpsePathJson?.let { Json.decodeFromString(APathSerializer, it) }

    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<CorpseDetailsRoute>()
    }
}
