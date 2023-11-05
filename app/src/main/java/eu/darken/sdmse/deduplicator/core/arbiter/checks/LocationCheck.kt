package eu.darken.sdmse.deduplicator.core.arbiter.checks

import dagger.Reusable
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCheck
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import javax.inject.Inject

@Reusable
class LocationCheck @Inject constructor(
    private val forensics: FileForensics,
) : ArbiterCheck {

    suspend fun favorite(before: List<Duplicate>, criterium: ArbiterCriterium.Location): List<Duplicate> {
        val withAreaInfo = before.map { dupe ->
            val areaInfo = forensics.identifyArea(dupe.path)
            if (areaInfo != null) {
                if (Bugs.isTrace) log(TAG, VERBOSE) { "${areaInfo.dataArea.type} - ${dupe.path}" }
            } else {
                log(TAG, WARN) { "Failed to determine area for $dupe" }

            }
            dupe to areaInfo
        }

        val sorted = when (criterium.mode) {
            ArbiterCriterium.Location.Mode.PREFER_PRIMARY -> withAreaInfo.sortedByDescending {
                it.second?.dataArea?.flags?.contains(DataArea.Flag.PRIMARY) == true
            }

            ArbiterCriterium.Location.Mode.PREFER_SECONDARY -> withAreaInfo.sortedByDescending {
                it.second?.dataArea?.flags?.contains(DataArea.Flag.PRIMARY) == false
            }
        }

        return sorted.map { it.first }
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Arbiter", "LocationCheck")
    }
}