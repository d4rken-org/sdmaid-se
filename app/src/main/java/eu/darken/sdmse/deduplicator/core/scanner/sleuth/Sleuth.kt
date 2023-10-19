package eu.darken.sdmse.deduplicator.core.scanner.sleuth

import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.deduplicator.core.types.Duplicate

interface Sleuth : Progress.Host, Progress.Client {

    suspend fun investigate(): Collection<Duplicate.Group>

    interface Factory {
        suspend fun isEnabled(): Boolean
        suspend fun create(): Sleuth
    }
}