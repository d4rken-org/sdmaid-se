package eu.darken.sdmse.analyzer.core.content

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType

data class ContentItem(
    val path: APath,
    val label: CaString = path.path.toCaString(),
    val size: Long,
    val type: FileType = FileType.DIRECTORY,
    val children: Set<ContentItem> = emptySet(),
)
