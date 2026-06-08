package eu.darken.sdmse.exclusion.core

import dagger.Reusable
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.exclusion.core.types.DefaultExclusion
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@Reusable
class DefaultExclusions @Inject constructor(
    private val exclusionSettings: ExclusionSettings,
) {

    // IDs of the built-in defaults. IDs ignore tags/settings, so this is also the identity used to
    // detect whether a default is still effective (not removed and not shadowed by a user exclusion).
    val defaultIds: Set<ExclusionId> = DATA.map { it.id }.toSet()

    init {
        // Two defaults sharing an ID would collapse for detection/removal/restore.
        require(DATA.size == defaultIds.size) { "Duplicate default exclusion IDs: $DATA" }
    }

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
        val known = ids.filter(defaultIds::contains).toSet()
        log(TAG) { "remove($ids) -> known default IDs: $known" }
        if (known.isEmpty()) return
        exclusionSettings.removedDefaultExclusions.update { it + known }
    }

    companion object {
        private val TAG = logTag("Exclusion", "Defaults")

        private val DATA = setOf(
            DefaultExclusion(
                "https://github.com/d4rken-org/sdmaid-se/issues/618",
                PkgExclusion("com.starfinanz.mobile.android.pushtan".toPkgId(), setOf(Exclusion.Tag.APPCLEANER)),
            ),
            DefaultExclusion(
                "https://github.com/d4rken-org/sdmaid-se/issues/618",
                PkgExclusion("de.zollsoft.impfapp".toPkgId(), setOf(Exclusion.Tag.APPCLEANER)),
            ),
            DefaultExclusion(
                "https://github.com/d4rken-org/sdmaid-se/issues/1331",
                PathExclusion(LocalPath.build("data", "rootfs"), setOf(Exclusion.Tag.SYSTEMCLEANER)),
            ),
        )
    }
}