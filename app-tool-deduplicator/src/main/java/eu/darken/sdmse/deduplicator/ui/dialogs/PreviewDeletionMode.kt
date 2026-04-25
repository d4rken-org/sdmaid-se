package eu.darken.sdmse.deduplicator.ui.dialogs

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.deduplicator.core.Duplicate

/**
 * Describes what set of items the [PreviewDeletionDialog] should preview and confirm deletion of.
 * Mirrors the legacy `PreviewDeletionDialog.Mode` so dashboard/list/cluster call sites map 1:1.
 */
sealed interface PreviewDeletionMode {
    val allowDeleteAll: Boolean
    val count: Int
    val previews: List<APathLookup<*>>

    data class All(val clusters: Collection<Duplicate.Cluster>) : PreviewDeletionMode {
        override val allowDeleteAll: Boolean = false
        override val count: Int get() = clusters.size
        override val previews: List<APathLookup<*>> get() = clusters.map { it.previewFile }
    }

    data class Clusters(
        val clusters: Collection<Duplicate.Cluster>,
        override val allowDeleteAll: Boolean,
    ) : PreviewDeletionMode {
        override val count: Int get() = clusters.size
        override val previews: List<APathLookup<*>> get() = clusters.map { it.previewFile }
    }

    data class Groups(
        val groups: Collection<Duplicate.Group>,
        override val allowDeleteAll: Boolean,
    ) : PreviewDeletionMode {
        override val count: Int get() = groups.size
        override val previews: List<APathLookup<*>> get() = groups.map { it.previewFile }
    }

    data class Duplicates(val duplicates: Collection<Duplicate>) : PreviewDeletionMode {
        override val allowDeleteAll: Boolean = false
        override val count: Int get() = duplicates.size
        override val previews: List<APathLookup<*>> get() = duplicates.map { it.lookup }
    }
}
