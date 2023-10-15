package eu.darken.sdmse.deduplicator.core.scanner.sleuth

interface Sleuth {
    interface Factory {
        suspend fun isEnabled(): Boolean
        suspend fun create(): Sleuth
    }
}