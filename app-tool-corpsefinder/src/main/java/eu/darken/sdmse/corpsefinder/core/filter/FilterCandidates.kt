package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.files.lookup
import eu.darken.sdmse.common.files.walk
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlin.reflect.KClass

/**
 * Turns the top level content of a single data area into corpses.
 *
 * Shared by [StandardCorpseFilter] and [DalvikCorpseFilter]. [filterType] is the filter that owns
 * the result, the risk settings are resolved by the caller because Dalvik reads them once for two
 * areas while the standard skeleton reads them per area.
 */
internal suspend fun filterCandidates(
    candidates: Collection<APath>,
    areaType: DataArea.Type,
    filterType: KClass<out CorpseFilter>,
    includeRiskKeeper: Boolean,
    includeRiskCommon: Boolean,
    tag: String,
    progress: Progress.Client,
    gatewaySwitch: GatewaySwitch,
    fileForensics: FileForensics,
    shouldExcludeCandidate: (APath) -> Boolean = { false },
    onOwnerFound: ((OwnerInfo) -> Unit)? = null,
): Collection<Corpse> {
    progress.updateProgressCount(Progress.Count.Percent(candidates.size))

    return candidates
        .asFlow()
        .filter { !shouldExcludeCandidate(it) }
        .mapNotNull {
            log(tag) { "Checking $it" }
            progress.increaseProgress()
            fileForensics.findOwners(it)
        }
        .filter { ownerInfo ->
            (ownerInfo.areaInfo.type == areaType).also {
                if (!it) log(tag, WARN) { "Wrong area: $ownerInfo" }
            }
        }
        .onEach { onOwnerFound?.invoke(it) }
        .filter { it.isCorpse }
        .filter { !it.isKeeper || includeRiskKeeper }
        .filter { !it.isCommon || includeRiskCommon }
        .map { ownerInfo ->
            val lookup = ownerInfo.item.lookup(gatewaySwitch)
            val content = if (lookup.isDirectory) ownerInfo.item.walk(gatewaySwitch).toSet() else emptyList()
            Corpse(
                filterType = filterType,
                ownerInfo = ownerInfo,
                lookup = lookup,
                content = content,
                isWriteProtected = false,
                riskLevel = when {
                    ownerInfo.isKeeper -> RiskLevel.KEEPER
                    ownerInfo.isCommon -> RiskLevel.COMMON
                    else -> RiskLevel.NORMAL
                }
            ).also { log(tag, INFO) { "Found Corpse: $it" } }
        }
        .toList()
}
