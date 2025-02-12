package eu.darken.sdmse.deduplicator.core.scanner

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.deduplicator.core.Duplicate
import kotlinx.coroutines.flow.Flow

interface Sleuth : Progress.Host, Progress.Client {

    suspend fun investigate(searchFlow: Flow<APathLookup<*>>): Set<Duplicate.Group>

    interface Factory {
        suspend fun create(): Sleuth
    }
}