package eu.darken.sdmse.compressor.ui.setup

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.compressor.core.CompressibleImage

sealed class CompressorSetupEvents {
    data class OpenPathPicker(val currentPaths: Set<APath>) : CompressorSetupEvents()
    data object NavigateToList : CompressorSetupEvents()
    data class ShowExample(val sampleImage: CompressibleImage, val quality: Int) : CompressorSetupEvents()
    data object NoExampleFound : CompressorSetupEvents()
}
