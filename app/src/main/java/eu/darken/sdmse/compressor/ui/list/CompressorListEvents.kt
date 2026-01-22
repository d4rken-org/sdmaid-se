package eu.darken.sdmse.compressor.ui.list

import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.compressor.core.tasks.CompressorTask

sealed class CompressorListEvents {
    data class ConfirmCompression(
        val items: Collection<CompressorListAdapter.Item>,
        val quality: Int,
    ) : CompressorListEvents()

    data class TaskResult(val result: CompressorTask.Result) : CompressorListEvents()
    data class ExclusionsCreated(val count: Int) : CompressorListEvents()

    data class PreviewEvent(val options: PreviewOptions) : CompressorListEvents()
}
