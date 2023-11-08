package eu.darken.sdmse.deduplicator.core.scanner

import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.deduplicator.core.Duplicate

interface Sleuth : Progress.Host, Progress.Client {

    suspend fun investigate(): Set<Duplicate.Group>

    interface Factory {
        suspend fun isEnabled(): Boolean
        suspend fun create(): Sleuth
    }
}