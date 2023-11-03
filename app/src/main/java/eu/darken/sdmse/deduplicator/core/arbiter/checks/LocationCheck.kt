package eu.darken.sdmse.deduplicator.core.arbiter.checks

import dagger.Reusable
import eu.darken.sdmse.common.areas.DataArea
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
        val withAreaInfo = before.map { it to forensics.identifyArea(it.path) }

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
}