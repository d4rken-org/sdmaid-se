package eu.darken.sdmse.corpsefinder.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.serialization.APathSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

@Serializable
data object CorpseFinderListRoute

@Serializable
data class CorpseDetailsRoute(
    val corpsePathJson: String? = null,
) {
    constructor(corpsePath: APath?) : this(
        corpsePathJson = corpsePath?.let { Json.encodeToString(APathSerializer, it) },
    )

    @Transient
    val corpsePath: APath? = corpsePathJson?.let { Json.decodeFromString(APathSerializer, it) }

    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<CorpseDetailsRoute>()
    }
}

@Serializable
data class CorpseRoute(
    val identifierJson: String,
) {
    constructor(identifier: APath) : this(
        identifierJson = Json.encodeToString(APathSerializer, identifier),
    )

    @Transient
    val identifier: APath = Json.decodeFromString(APathSerializer, identifierJson)

    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<CorpseRoute>()
    }
}
