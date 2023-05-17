package eu.darken.sdmse.analyzer.core.content.types

import eu.darken.sdmse.common.files.APathLookup

data class MediaContent(
    override val id: String,
    override val spaceUsed: Long,
) : StorageContent {

    data class MediaStat(
        val path: APathLookup<*>,
        val totalSize: Long,
    )

}