package eu.darken.sdmse.deduplicator.ui.details.cluster

import eu.darken.sdmse.common.files.APathLookup

sealed class ClusterEvents {
    data class ConfirmDeletion(
        val items: Collection<ClusterAdapter.Item>,
        val allowDeleteAll: Boolean
    ) : ClusterEvents()

    data class OpenDuplicate(val lookup: APathLookup<*>) : ClusterEvents()

    data class ViewDuplicate(val lookup: APathLookup<*>) : ClusterEvents()
}
