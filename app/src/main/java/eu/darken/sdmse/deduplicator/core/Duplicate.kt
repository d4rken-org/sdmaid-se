package eu.darken.sdmse.deduplicator.core

import android.os.Parcelable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import kotlinx.parcelize.Parcelize

interface Duplicate {
    val lookup: APathLookup<*>
    val label: CaString
        get() = lookup.userReadableName

    val path: APath
        get() = lookup.lookedUp

    val identifier: String
        get() = lookup.path

    val size: Long
        get() = lookup.size

    interface Group {
        val identifier: Identifier
        val duplicates: Set<Duplicate>
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
        data class Identifier(val value: String) : Parcelable
    }

    data class Cluster(
        val identifier: Identifier,
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
        data class Identifier(val value: String) : Parcelable
    }
}