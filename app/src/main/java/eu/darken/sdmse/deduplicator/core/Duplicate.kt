package eu.darken.sdmse.deduplicator.core

import android.os.Parcelable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import kotlinx.parcelize.Parcelize
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
        PHASH
    }

    @Parcelize
    data class Id(val value: String) : Parcelable

    interface Group {
        val identifier: Id
        val duplicates: Set<Duplicate>

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
            get() = duplicates.drop(1).sumOf { it.size }
        val count: Int
            get() = duplicates.size

        @Parcelize
        data class Id(val value: String) : Parcelable
    }

    data class Cluster(
        val identifier: Id,
        val groups: Set<Group>,
    ) {
        val averageSize: Double
            get() = groups.map { it.totalSize }.average()
        val totalSize: Long
            get() = groups.sumOf { it.totalSize }
        val redundantSize: Long
            get() = groups.sumOf { it.redundantSize }
        val count: Int
            get() = groups.sumOf { it.count }

        val previewFile: APathLookup<*>
            get() = groups.first().duplicates.first().lookup

        @Parcelize
        data class Id(val value: String) : Parcelable
    }
}