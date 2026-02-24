package eu.darken.sdmse.squeezer.ui.list

import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.squeezer.core.tasks.SqueezerTask

sealed class SqueezerListEvents {
    data class ConfirmCompression(
        val items: Collection<SqueezerListAdapter.Item>,
        val quality: Int,
    ) : SqueezerListEvents()

    data class TaskResult(val result: SqueezerTask.Result) : SqueezerListEvents()
    data class ExclusionsCreated(val count: Int) : SqueezerListEvents()

    data class PreviewEvent(val options: PreviewOptions) : SqueezerListEvents()
}
