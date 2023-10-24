package eu.darken.sdmse.deduplicator.ui.details.cluster

import eu.darken.sdmse.common.files.APathLookup

sealed class ClusterEvents {
    data class ConfirmDeletion(val items: Collection<ClusterAdapter.Item>) : ClusterEvents()
    data class ViewItem(val lookup: APathLookup<*>) : ClusterEvents()
}
