package eu.darken.sdmse.deduplicator.core.scanner

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumSleuth
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashSleuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import javax.inject.Inject


class DuplicatesScanner @Inject constructor(
    private val checksumFactory: ChecksumSleuth.Factory,
    private val phashFactory: PHashSleuth.Factory,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun scan(): Set<Duplicate.Cluster> {
        log(TAG) { "scan()" }

        val cksGroups: Set<ChecksumDuplicate.Group> = if (checksumFactory.isEnabled()) {
            log(TAG) { "$checksumFactory is enabled" }
            checksumFactory.create().withProgress(this) { investigate() }
        } else {
            log(TAG) { "$checksumFactory is disabled" }
            emptySet()
        }

        val phGroups = if (phashFactory.isEnabled()) {
            log(TAG) { "$phashFactory is enabled" }
            phashFactory.create().withProgress(this) { investigate() }
        } else {
            log(TAG) { "$phashFactory is disabled" }
            emptySet()
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        updateProgressSecondary()
        updateProgressCount(Progress.Count.Indeterminate())

        val overlaps = mutableMapOf<ChecksumDuplicate.Group, Set<PHashDuplicate.Group>>()
        val uniques = mutableSetOf<PHashDuplicate.Group>()

        // Sort phash results, which need to be grouped with others, and which are unique?
        phGroups.forEach { phGrp ->
            val phashPaths = phGrp.duplicates.map { it.path }.toSet()

            val cksOverlaps = cksGroups.filter { cksGrp ->
                cksGrp.duplicates.any { phashPaths.contains(it.path) }
            }

            when {
                cksOverlaps.isNotEmpty() -> {
                    cksOverlaps.forEach { overlaps[it] = (overlaps[it] ?: emptySet()).plus(phGrp) }
                }

                else -> {
                    uniques.add(phGrp)
                }
            }
        }

        val clusters = mutableSetOf<Duplicate.Cluster>()

        val cksCoveredPaths = cksGroups
            .map { it.duplicates }
            .flatten()
            .map { it.path }
            .toSet()

        cksGroups
            .map { cksGrp ->
                val overlapping = overlaps.remove(cksGrp)
                log(TAG, VERBOSE) { "${overlapping?.size} groups overlap with ChecksumGroup $cksGrp" }

                if (Bugs.isTrace) overlapping?.forEachIndexed { index, group -> log(TAG, VERBOSE) { "#$index $group" } }


                val grps = mutableSetOf<Duplicate.Group>(cksGrp)

                overlapping
                    ?.map { phGrp ->
                        // If some dupes are already covered by checksum matches, then they take precedence
                        // A similarity match that already has a checksum match, will not be shown as similarity match
                        val uniquePhDupes = phGrp.duplicates
                            .filter { phDupe -> !cksCoveredPaths.contains(phDupe.path) }
                            .toSet()
                        phGrp.copy(duplicates = uniquePhDupes)
                    }
                    ?.filter { it.duplicates.isNotEmpty() }
                    ?.let { grps.addAll(it) }

                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id(UUID.randomUUID().toString()),
                    groups = grps
                )
            }
            .run { clusters.addAll(this) }

        uniques
            .map { phGrp ->
                if (Bugs.isTrace) log(TAG, VERBOSE) { "Unique PHGroup: $phGrp" }
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id(UUID.randomUUID().toString()),
                    groups = setOf(phGrp)
                )
            }
            .run { clusters.addAll(this) }

        if (Bugs.isTrace) {
            clusters.forEach { c ->
                log(
                    TAG,
                    VERBOSE
                ) { "performScan(): Cluster ${c.identifier}: ${c.groups.size} groups, ${c.count} dupes" }
                c.groups.forEach { g ->
                    log(TAG, VERBOSE) { "performScan():  Group ${g.identifier}: ${g.duplicates.size} dupes" }
                    g.duplicates.forEach { d ->
                        log(TAG, VERBOSE) { "performScan():   Duplicate: $d" }
                    }
                }
            }
        }
        return clusters
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Scanner")
    }
}