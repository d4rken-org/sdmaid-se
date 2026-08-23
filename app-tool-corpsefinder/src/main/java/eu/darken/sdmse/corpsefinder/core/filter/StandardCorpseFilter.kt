package eu.darken.sdmse.corpsefinder.core.filter

import androidx.annotation.StringRes
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.listFiles
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.pathExclusions
import eu.darken.sdmse.exclusion.core.types.PathExclusionIndex
import eu.darken.sdmse.main.core.SDMTool

/**
 * The scan skeleton shared by the filters that check the top level content of a single data area:
 * gate on the gateway's capabilities, list each area, drop excluded paths, hand the rest to
 * [filterCandidates].
 *
 * [DalvikCorpseFilter] is not one of these, it walks two areas in one scan.
 */
abstract class StandardCorpseFilter(
    private val filterTag: String,
    private val areaType: DataArea.Type,
    @param:StringRes private val defaultProgressLabel: Int,
    @param:StringRes private val scanProgressLabel: Int,
    private val capabilityPolicy: CapabilityPolicy,
    /** Skip the scan entirely from this API level on, for areas that no longer exist. */
    private val apiBailAt: Int? = null,
    /** Scan but withhold the findings from this API level on, see [CorpseFilter.untestedApiCeiling]. */
    untestedFromApi: Int? = null,
    private val indeterminateWhileListing: Boolean = false,
    private val excludedNames: Set<String> = emptySet(),
    private val onOwnerFound: ((OwnerInfo) -> Unit)? = null,
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val corpseFinderSettings: CorpseFinderSettings,
    private val exclusionManager: ExclusionManager,
) : CorpseFilter(filterTag, Progress.Data(primary = defaultProgressLabel.toCaString())) {

    final override val untestedApiCeiling: Int? = untestedFromApi

    /** What the [LocalGateway] has to offer before a scan makes sense. */
    sealed interface CapabilityPolicy {
        /** No gateway is needed at all. */
        data object None : CapabilityPolicy

        data object AlwaysRoot : CapabilityPolicy

        data class RootFromApi(val api: Int) : CapabilityPolicy

        data class RootOrAdbFromApi(val api: Int) : CapabilityPolicy
    }

    final override suspend fun doScan(): Collection<Corpse> {
        log(filterTag) { "Scanning..." }

        if (!hasRequiredCapabilities()) return emptySet()

        if (apiBailAt != null && hasApiLevel(apiBailAt)) {
            log(filterTag, WARN) { "Area is no longer used from API $apiBailAt on, skipping." }
            return emptySet()
        }

        updateProgressPrimary(scanProgressLabel)

        val exclusionIndex = PathExclusionIndex(exclusionManager.pathExclusions(SDMTool.Type.CORPSEFINDER))

        return areaManager.currentAreas()
            .filter { it.type == areaType }
            .map { area ->
                updateProgressSecondary {
                    it.getString(eu.darken.sdmse.common.R.string.general_progress_processing_x, area.label.get(it))
                }
                if (indeterminateWhileListing) updateProgressCount(Progress.Count.Indeterminate())

                log(filterTag) { "Reading $area" }
                val topLevelContents = area.path
                    .listFiles(gatewaySwitch)
                    .filter { path ->
                        val isExcluded = exclusionIndex.matches(path)
                        if (isExcluded) log(filterTag, INFO) { "Excluded due to path exclusion: $path" }
                        !isExcluded
                    }

                log(filterTag) { "Filtering $area" }
                doFilter(topLevelContents)
            }
            .flatten()
    }

    private suspend fun hasRequiredCapabilities(): Boolean = when (val policy = capabilityPolicy) {
        is CapabilityPolicy.None -> true

        is CapabilityPolicy.AlwaysRoot -> {
            val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway
            if (gateway.hasRoot()) {
                true
            } else {
                log(filterTag) { "LocalGateway has no root, skipping." }
                false
            }
        }

        is CapabilityPolicy.RootFromApi -> {
            val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway
            if (hasApiLevel(policy.api) && !gateway.hasRoot()) {
                log(filterTag) { "LocalGateway has no root, skipping public data on Android 13" }
                false
            } else {
                true
            }
        }

        is CapabilityPolicy.RootOrAdbFromApi -> {
            val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway
            if (hasApiLevel(policy.api) && !gateway.hasRoot() && !gateway.hasAdb()) {
                log(filterTag) { "LocalGateway has no root/adb, skipping public data on Android 13+" }
                false
            } else {
                true
            }
        }
    }

    private suspend fun doFilter(candidates: Collection<APath>): Collection<Corpse> = filterCandidates(
        candidates = candidates,
        areaType = areaType,
        filterType = this::class,
        includeRiskKeeper = corpseFinderSettings.includeRiskKeeper.value(),
        includeRiskCommon = corpseFinderSettings.includeRiskCommon.value(),
        tag = filterTag,
        progress = this,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        shouldExcludeCandidate = { shouldExcludeCandidate(it) },
        onOwnerFound = onOwnerFound,
    )

    private fun shouldExcludeCandidate(path: APath): Boolean = excludedNames.contains(path.name)
}
