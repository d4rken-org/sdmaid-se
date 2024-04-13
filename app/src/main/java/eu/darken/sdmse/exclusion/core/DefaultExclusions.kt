package eu.darken.sdmse.exclusion.core

import dagger.Reusable
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.exclusion.core.types.DefaultExclusion
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@Reusable
class DefaultExclusions @Inject constructor(
    private val exclusionSettings: ExclusionSettings,
) {
    // Due to a bug before v0.23.3-beta0, some of the user exclusions can be inside the removedDefaultExclusions
    // These are just the strings IDs though and shouldn't cause any issue
    // Downstream we don't combine removed defaults and user exclusions
    val exclusions: Flow<Collection<DefaultExclusion>> = exclusionSettings.removedDefaultExclusions.flow
        .onEach { log(TAG, INFO) { "Removed default exclusions are: $it" } }
        .map { removed -> DATA.filter { !removed.contains(it.id) } }

    suspend fun reset() {
        log(TAG) { "reset()" }
        exclusionSettings.removedDefaultExclusions.value(emptySet())
    }

    suspend fun remove(ids: Set<ExclusionId>) {
        log(TAG) { "remove($ids)" }
        exclusionSettings.removedDefaultExclusions.update { it + ids }
    }

    companion object {
        private val TAG = logTag("Exclusion", "Defaults")

        private val DATA = mutableSetOf(
            DefaultExclusion(
                "https://github.com/d4rken-org/sdmaid-se/issues/618",
                PkgExclusion("com.starfinanz.mobile.android.pushtan".toPkgId(), setOf(Exclusion.Tag.APPCLEANER)),
            ),
            DefaultExclusion(
                "https://github.com/d4rken-org/sdmaid-se/issues/618",
                PkgExclusion("de.zollsoft.impfapp".toPkgId(), setOf(Exclusion.Tag.APPCLEANER)),
            )
        )
    }
}