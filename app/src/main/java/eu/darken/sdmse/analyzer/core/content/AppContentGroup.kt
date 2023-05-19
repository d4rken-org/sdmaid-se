package eu.darken.sdmse.analyzer.core.content

import eu.darken.sdmse.common.ca.CaString

data class AppContentGroup(
    override val id: ContentGroup.Id = ContentGroup.Id(),
    override val label: CaString?,
    override val contents: Collection<ContentItem> = emptyList(),
) : ContentGroup {

    companion object {
        fun from(
            label: CaString?,
            vararg content: ContentItem
        ) = AppContentGroup(
            label = label,
            contents = content.toList()
        )
    }
}
