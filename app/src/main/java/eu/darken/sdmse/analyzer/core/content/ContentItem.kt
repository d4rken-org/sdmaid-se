package eu.darken.sdmse.analyzer.core.content

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType

data class ContentItem(
    val path: APath,
    val label: CaString = path.path.toCaString(),
    val itemSize: Long?,
    val type: FileType,
    val children: Collection<ContentItem>?,
) {

    val size: Long? = when (type) {
        FileType.FILE -> itemSize
        FileType.DIRECTORY -> children?.let { cs ->
            cs.sumOf { it.size ?: 0L } + (itemSize ?: 4096L)
        }

        else -> null
    }

    companion object {
        fun fromInaccessible(path: APath): ContentItem = ContentItem(
            path = path,
            type = FileType.DIRECTORY,
            itemSize = null,
            children = null,
        )

        fun fromLookup(lookup: APathLookup<*>, children: Collection<ContentItem>? = null): ContentItem = ContentItem(
            path = lookup.lookedUp,
            children = children,
            itemSize = when (lookup.fileType) {
                FileType.FILE -> lookup.size
                else -> 4096L
            },
            type = lookup.fileType,
        )
    }
}