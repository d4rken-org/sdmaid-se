package eu.darken.sdmse.analyzer.core.content.types

data class AppContent(
    override val id: String,
    override val spaceUsed: Long,
) : StorageContent