package eu.darken.sdmse.analyzer.core.content

import eu.darken.sdmse.common.ca.CaString

data class MediaContentGroup(
    override val id: ContentGroup.Id = ContentGroup.Id(),
    override val label: CaString?,
    override val contents: Collection<ContentItem> = emptyList(),
) : ContentGroup