package eu.darken.sdmse.deduplicator.core.scanner

import eu.darken.sdmse.deduplicator.core.scanner.sleuth.Sleuth
import javax.inject.Inject


class DuplicatesScanner @Inject constructor(
    val sleuths: Set<Sleuth>
)