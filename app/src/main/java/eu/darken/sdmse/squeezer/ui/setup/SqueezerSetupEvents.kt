package eu.darken.sdmse.squeezer.ui.setup

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.squeezer.core.CompressibleImage

sealed class SqueezerSetupEvents {
    data class OpenPathPicker(val currentPaths: Set<APath>) : SqueezerSetupEvents()
    data object NavigateToList : SqueezerSetupEvents()
    data class ShowExample(val sampleImage: CompressibleImage, val quality: Int) : SqueezerSetupEvents()
    data object NoExampleFound : SqueezerSetupEvents()
    data object NoResultsFound : SqueezerSetupEvents()
}
