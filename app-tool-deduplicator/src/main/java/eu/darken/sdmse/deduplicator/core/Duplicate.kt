package eu.darken.sdmse.deduplicator.core

import android.os.Parcelable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.time.Instant

interface Duplicate {
    val lookup: APathLookup<*>

    val modifiedAt: Instant
        get() = lookup.modifiedAt

    val label: CaString
        get() = lookup.userReadableName

    val path: APath
        get() = lookup.lookedUp

    val identifier: Id
        get() = Id(lookup.path)

    val size: Long
        get() = lookup.size

    val type: Type

    enum class Type {
        CHECKSUM,
        PHASH,
        MEDIA,
    }

    @Parcelize
    data class Id(val value: String) : Parcelable

    interface Group {
        val identifier: Id
        val duplicates: Set<Duplicate>
        val keeperIdentifier: Duplicate.Id?
            get() = null

        val type: Type

        val label: CaString
            get() = identifier.value.toCaString()

        val previewFile: APathLookup<*>
            get() = duplicates.first().lookup

        val totalSize: Long
            get() = duplicates.sumOf { it.size }
        val averageSize: Double
            get() = duplicates.map { it.size }.average()
        val redundantSize: Long
            get() {
                val keeperSize = keeperIdentifier
                    ?.let { kid -> duplicates.firstOrNull { it.identifier == kid }?.size }
                    ?: duplicates.firstOrNull()?.size
                    ?: 0L
                return totalSize - keeperSize
            }
        val count: Int
            get() = duplicates.size

        @Parcelize
        data class Id(val value: String) : Parcelable
    }

    data class Cluster(
        val identifier: Id,
        val groups: Set<Group>,
        val favoriteGroupIdentifier: Group.Id? = null,
    ) {
        val averageSize: Double
            get() = groups.map { it.totalSize }.average()
        val totalSize: Long
            get() = groups.sumOf { it.totalSize }
        val redundantSize: Long
            get() {
                val favId = favoriteGroupIdentifier
                return groups.sumOf { group ->
                    // The favorite group's keeper survives a keep-one delete; a single-member
                    // favorite group IS the keeper and frees nothing.
                    if (group.identifier == favId) group.redundantSize else group.totalSize
                }
            }

        /**
         * Files a default keep-one delete removes: every duplicate except the favorite group's
         * keeper, distinct by id so cross-group path overlap isn't double-counted. The count
         * counterpart of [redundantSize]; exact vs DuplicatesDeleter for normalized (scanned)
         * data. A single-member favorite group contributes nothing - its sole member is the kept
         * copy. The keeper-less fallback mirrors [redundantSize] and is defensive only — the
         * scanner always assigns a keeper to every group with >= 2 members.
         */
        val redundantCount: Int
            get() {
                val favId = favoriteGroupIdentifier
                return groups.flatMap { group ->
                    if (group.identifier == favId) {
                        val keeperId = group.keeperIdentifier ?: group.duplicates.firstOrNull()?.identifier
                        group.duplicates.filterNot { it.identifier == keeperId }.map { it.identifier }
                    } else {
                        group.duplicates.map { it.identifier }
                    }
                }.toSet().size
            }

        /**
         * The duplicate copies that a default (keep-one) delete would remove from this cluster:
         * every duplicate except the keeper of the favorite group. Single source of truth shared by the
         * list row model (what the list marks/frees), the list's delete confirmation (what the confirm
         * dialog reports) and the details screen (which rows it marks), so the displayed "freeable"
         * always matches what is actually deleted.
         *
         * It mirrors the keeper/favorite assignment the scanner writes for every scanned cluster, so it
         * matches DuplicatesDeleter's non-deleteAll path for scanned data. (Pre-scan clusters with a null
         * favorite/keeper never reach these screens, so the keeper-less fallback here is defensive only.)
         */
        val deleteTargetIds: Set<Duplicate.Id>
            get() {
                val favId = favoriteGroupIdentifier
                return groups.flatMap { group ->
                    if (group.identifier == favId) {
                        val keeper = group.keeperIdentifier
                        if (keeper != null) {
                            group.duplicates.filter { it.identifier != keeper }.map { it.identifier }
                        } else {
                            emptyList()
                        }
                    } else {
                        group.duplicates.map { it.identifier }
                    }
                }.toSet()
            }

        /**
         * The file a set delete keeps: the favorite group's keeper, or its sole member if it has only one.
         */
        val keptDuplicateId: Duplicate.Id?
            get() {
                val favorite = groups.firstOrNull { it.identifier == favoriteGroupIdentifier } ?: return null
                return favorite.keeperIdentifier ?: favorite.duplicates.singleOrNull()?.identifier
            }

        /**
         * Caps a folder delete so one file of this cluster survives.
         * Cluster {a, b, c}, kept a: {a, b, c} -> {b, c}; {a, b} -> {a, b}; {b, x} -> {b} (x is not in this cluster).
         */
        fun keepOneOf(targetIds: Set<Duplicate.Id>): Set<Duplicate.Id> {
            val clusterIds = groups.flatMap { group -> group.duplicates.map { it.identifier } }.toSet()
            val live = clusterIds.filter { targetIds.contains(it) }.toSet()
            if (!live.containsAll(clusterIds)) return live
            val keptId = keptDuplicateId ?: live.firstOrNull() ?: return live
            return live - keptId
        }

        val count: Int
            get() = groups.sumOf { it.count }
        val types: Set<Type>
            get() = groups.map { it.type }.toSet()

        val previewFile: APathLookup<*>
            get() = groups.first().previewFile

        @Serializable
        @Parcelize
        data class Id(val value: String) : Parcelable
    }
}