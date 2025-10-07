package eu.darken.sdmse.deduplicator.ui.details.cluster

import android.content.Intent
import eu.darken.sdmse.common.previews.PreviewOptions

sealed class ClusterEvents {
    data class ConfirmDeletion(
        val items: Collection<ClusterAdapter.Item>,
        val allowDeleteAll: Boolean
    ) : ClusterEvents()

    data class OpenDuplicate(val intent: Intent) : ClusterEvents()

    data class ViewDuplicate(val options: PreviewOptions) : ClusterEvents()
}
