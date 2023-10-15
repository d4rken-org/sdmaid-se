package eu.darken.sdmse.deduplicator.core.types

import android.os.Parcelable
import eu.darken.sdmse.common.files.APathLookup
import kotlinx.parcelize.Parcelize

interface Duplicate {
    val lookup: APathLookup<*>

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
}