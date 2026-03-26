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
                    if (group.identifier == favId && group.count >= 2) group.redundantSize else group.totalSize
                }
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