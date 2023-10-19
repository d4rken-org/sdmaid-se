package eu.darken.sdmse.deduplicator.core.types

import android.os.Parcelable
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import kotlinx.parcelize.Parcelize

interface Duplicate {
    val lookup: APathLookup<*>

    val path: APath
        get() = lookup.lookedUp

    val identifier: String
        get() = lookup.path

    val size: Long
        get() = lookup.size

    interface Group {
        val identifier: Identifier
        val duplicates: Collection<Duplicate>

        val size: Long
            get() = duplicates.sumOf { it.size }
        val count: Int
            get() = duplicates.size

        @Parcelize
        data class Identifier(val string: String) : Parcelable
    }

    data class Cluster(
        val identifier: Identifier,
        val groups: Collection<Group>,
    ) {

        val averageSize: Double
            get() = groups.map { it.size }.average()
        val totalSize: Long
            get() = groups.sumOf { it.size }
        val count: Int
            get() = groups.sumOf { it.count }

        val previewFile: APathLookup<*>
            get() = groups.first().duplicates.first().lookup

        @Parcelize
        data class Identifier(val string: String) : Parcelable
    }
}