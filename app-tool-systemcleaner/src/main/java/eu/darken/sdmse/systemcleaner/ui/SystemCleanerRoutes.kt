package eu.darken.sdmse.systemcleaner.ui

import kotlinx.serialization.Serializable

@Serializable
data object SystemCleanerListRoute

@Serializable
data class FilterContentDetailsRoute(
    val filterIdentifier: String? = null,
)

@Serializable
data class FilterContentRoute(
    val identifier: String,
)

