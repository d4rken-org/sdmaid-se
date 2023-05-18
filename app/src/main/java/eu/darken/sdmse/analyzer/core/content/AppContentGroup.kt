package eu.darken.sdmse.analyzer.core.content

data class AppContentGroup(
    override val id: ContentGroup.Id = ContentGroup.Id(),
    override val contents: Collection<ContentItem> = emptyList(),
) : ContentGroup {

    companion object {
        fun from(vararg content: ContentItem) = AppContentGroup(contents = content.toList())
    }
}
