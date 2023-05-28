package eu.darken.sdmse.analyzer.core.content

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType

data class ContentItem(
    val path: APath,
    val lookup: APathLookup<*>?,
    val label: CaString = path.path.toCaString(),
    val itemSize: Long?,
    val type: FileType,
    val children: Collection<ContentItem> = emptySet(),
    val inaccessible: Boolean,
) {

    val size: Long? = when (type) {
        FileType.FILE -> itemSize
        FileType.DIRECTORY -> itemSize?.let { item ->
            item + children.sumOf { it.size ?: 0L }
        }

        else -> null
    }

    companion object {
        fun fromInaccessible(path: APath, size: Long? = null): ContentItem = ContentItem(
            path = path,
            lookup = null,
            type = FileType.DIRECTORY,
            itemSize = size,
            inaccessible = true,
        )

        fun fromLookup(lookup: APathLookup<*>): ContentItem = ContentItem(
            path = lookup.lookedUp,
            lookup = lookup,
            itemSize = when (lookup.fileType) {
                FileType.FILE -> lookup.size
                else -> 4096L
            },
            type = lookup.fileType,
            inaccessible = false,
        )
    }
}