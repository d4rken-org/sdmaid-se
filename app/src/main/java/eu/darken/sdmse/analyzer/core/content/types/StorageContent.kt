package eu.darken.sdmse.analyzer.core.content.types

sealed interface StorageContent {
    val id: String
    val spaceUsed: Long
}