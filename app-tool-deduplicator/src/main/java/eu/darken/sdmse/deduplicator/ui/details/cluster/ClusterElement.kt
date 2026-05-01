package eu.darken.sdmse.deduplicator.ui.details.cluster

import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.media.MediaDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate

internal sealed interface ClusterElement {
    val key: String

    data class ClusterHeader(val cluster: Duplicate.Cluster) : ClusterElement {
        override val key: String = "cluster_${cluster.identifier.value}"
    }

    data class DirectoryHeader(
        val group: DirectoryGroup,
        val isCollapsed: Boolean,
    ) : ClusterElement {
        override val key: String = "dir_${group.identifier.value}"
    }

    sealed interface GroupHeader : ClusterElement {
        val group: Duplicate.Group
        val willBeDeleted: Boolean
    }

    data class ChecksumGroupHeader(
        override val group: ChecksumDuplicate.Group,
        override val willBeDeleted: Boolean,
    ) : GroupHeader {
        override val key: String = "csgrp_${group.identifier.value}"
    }

    data class PHashGroupHeader(
        override val group: PHashDuplicate.Group,
        override val willBeDeleted: Boolean,
    ) : GroupHeader {
        override val key: String = "phgrp_${group.identifier.value}"
    }

    data class MediaGroupHeader(
        override val group: MediaDuplicate.Group,
        override val willBeDeleted: Boolean,
    ) : GroupHeader {
        override val key: String = "mgrp_${group.identifier.value}"
    }

    sealed interface DuplicateRow : ClusterElement {
        val duplicate: Duplicate
        val willBeDeleted: Boolean
        val groupId: Duplicate.Group.Id
    }

    data class ChecksumDuplicateRow(
        override val duplicate: ChecksumDuplicate,
        override val willBeDeleted: Boolean,
        override val groupId: Duplicate.Group.Id,
    ) : DuplicateRow {
        override val key: String = "csdup_${duplicate.identifier.value}"
    }

    data class PHashDuplicateRow(
        override val duplicate: PHashDuplicate,
        override val willBeDeleted: Boolean,
        override val groupId: Duplicate.Group.Id,
    ) : DuplicateRow {
        override val key: String = "phdup_${duplicate.identifier.value}"
    }

    data class MediaDuplicateRow(
        override val duplicate: MediaDuplicate,
        override val willBeDeleted: Boolean,
        override val groupId: Duplicate.Group.Id,
    ) : DuplicateRow {
        override val key: String = "mdup_${duplicate.identifier.value}"
    }
}

internal fun buildClusterElements(
    cluster: Duplicate.Cluster,
    isDirectoryView: Boolean,
    collapsed: Set<DirectoryGroup.Id>,
): List<ClusterElement> {
    val elements = mutableListOf<ClusterElement>()
    elements.add(ClusterElement.ClusterHeader(cluster))
    if (isDirectoryView) {
        addDirectoryViewElements(cluster, collapsed, elements)
    } else {
        addGroupViewElements(cluster, elements)
    }
    return elements
}

private fun addGroupViewElements(
    cluster: Duplicate.Cluster,
    elements: MutableList<ClusterElement>,
) {
    val favoriteGroupId = cluster.favoriteGroupIdentifier
    cluster.groups
        .sortedByDescending { it.totalSize }
        .forEach { group ->
            val keeperId = group.keeperIdentifier
            val isNonFavoriteGroup = favoriteGroupId != null
                && cluster.groups.size >= 2
                && group.identifier != favoriteGroupId

            when (group.type) {
                Duplicate.Type.CHECKSUM -> {
                    group as ChecksumDuplicate.Group
                    elements.add(ClusterElement.ChecksumGroupHeader(group, willBeDeleted = isNonFavoriteGroup))
                    group.duplicates.forEach { dupe ->
                        elements.add(
                            ClusterElement.ChecksumDuplicateRow(
                                duplicate = dupe,
                                willBeDeleted = keeperId != null && dupe.identifier != keeperId,
                                groupId = group.identifier,
                            )
                        )
                    }
                }

                Duplicate.Type.PHASH -> {
                    group as PHashDuplicate.Group
                    elements.add(ClusterElement.PHashGroupHeader(group, willBeDeleted = isNonFavoriteGroup))
                    group.duplicates.forEach { dupe ->
                        elements.add(
                            ClusterElement.PHashDuplicateRow(
                                duplicate = dupe,
                                willBeDeleted = keeperId != null && dupe.identifier != keeperId,
                                groupId = group.identifier,
                            )
                        )
                    }
                }

                Duplicate.Type.MEDIA -> {
                    group as MediaDuplicate.Group
                    elements.add(ClusterElement.MediaGroupHeader(group, willBeDeleted = isNonFavoriteGroup))
                    group.duplicates.forEach { dupe ->
                        elements.add(
                            ClusterElement.MediaDuplicateRow(
                                duplicate = dupe,
                                willBeDeleted = keeperId != null && dupe.identifier != keeperId,
                                groupId = group.identifier,
                            )
                        )
                    }
                }
            }
        }
}

private fun addDirectoryViewElements(
    cluster: Duplicate.Cluster,
    collapsed: Set<DirectoryGroup.Id>,
    elements: MutableList<ClusterElement>,
) {
    val deleteTargetIds = cluster.groups
        .filter { it.keeperIdentifier != null && it.duplicates.size >= 2 }
        .flatMap { group ->
            group.duplicates
                .filter { it.identifier != group.keeperIdentifier }
                .map { it.identifier }
        }
        .toSet()

    val dupeToGroupId = cluster.groups.flatMap { group ->
        group.duplicates.map { it.identifier to group.identifier }
    }.toMap()

    val allDuplicates = cluster.groups.flatMap { it.duplicates }
    val directoryGroups = allDuplicates
        .groupBy { it.path.segments.dropLast(1) }
        .map { (parentSegments, duplicates) ->
            DirectoryGroup(
                parentSegments = parentSegments,
                duplicates = duplicates.sortedBy { it.path.name },
            )
        }
        .sortedByDescending { it.totalSize }

    directoryGroups.forEach { dirGroup ->
        val isCollapsed = collapsed.contains(dirGroup.identifier)
        elements.add(ClusterElement.DirectoryHeader(group = dirGroup, isCollapsed = isCollapsed))

        if (!isCollapsed) {
            dirGroup.duplicates.forEach { dupe ->
                val willBeDeleted = dupe.identifier in deleteTargetIds
                val groupId = dupeToGroupId[dupe.identifier] ?: return@forEach
                val element = when (dupe) {
                    is ChecksumDuplicate -> ClusterElement.ChecksumDuplicateRow(dupe, willBeDeleted, groupId)
                    is PHashDuplicate -> ClusterElement.PHashDuplicateRow(dupe, willBeDeleted, groupId)
                    is MediaDuplicate -> ClusterElement.MediaDuplicateRow(dupe, willBeDeleted, groupId)
                    else -> return@forEach
                }
                elements.add(element)
            }
        }
    }
}
