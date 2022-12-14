package eu.darken.sdmse.corpsefinder.core.filter

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.*
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.toLocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import javax.inject.Inject

@Reusable
class SdcardCorpseFilter @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val corpseFinderSettings: CorpseFinderSettings,
    private val clutterRepo: ClutterRepo,
    private val pkgRepo: PkgRepo,
) : CorpseFilter(
    TAG,
    appScope = appScope
) {

    private val fileCache: MutableMap<CacheKey, Collection<AreaInfo>> = HashMap()

    override suspend fun scan(): Collection<Corpse> {
        if (!corpseFinderSettings.filterSdcardEnabled.value()) {
            log(TAG) { "Filter is disabled" }
            return emptyList()
        }
        log(TAG) { "Scanning..." }

        gatewaySwitch.addParent(this)
        updateProgressPrimary("SDCARD")

        return doReverseCSI()
    }

    /**
     * We basically do a reverse CSI.
     * Instead of looking who owns a path, we look at all owners we know, and see if any exists.
     * This works because SDCARD is not a blacklist location, items have to be whitelisted via clutterdb.
     */
    private suspend fun doReverseCSI(): Collection<Corpse> {
        val potentialCorpses = findPotentialCorpses()
        log(TAG) { "Got $potentialCorpses potential corpses to check" }

        // Deep search each possible corpse
        updateProgressSecondary(R.string.general_progress_filtering)
        updateProgressCount(Progress.Count.Counter(0, potentialCorpses.size * 2))

        val deadItems = mutableSetOf<OwnerInfo>()
        val aliveItems = mutableSetOf<OwnerInfo>()

        val includeRiskKeeper: Boolean = corpseFinderSettings.includeRiskKeeper.value()
        val includeRiskCommon: Boolean = corpseFinderSettings.includeRiskCommon.value()

        potentialCorpses.forEach { canidate ->
            when {
                canidate.isOwned -> {
                    aliveItems.add(canidate)
                    log(TAG, VERBOSE) { "Alive item, owner installed (can block other corpses): ${canidate.item}" }
                }
                canidate.isKeeper && !includeRiskKeeper -> {
                    aliveItems.add(canidate)
                    log(TAG, VERBOSE) { "Alive: Excluded keepers (can block other corpses): ${canidate.item}" }
                }
                canidate.isCommon && !includeRiskCommon -> {
                    aliveItems.add(canidate)
                    log(TAG, VERBOSE) { "Alive: Excluded common items (can block other corpses): ${canidate.item}" }
                }
                canidate.isCorpse -> {
                    deadItems.add(canidate)
                    log(TAG, VERBOSE) { "Dead: Possible dead item (if not blocked): ${canidate.item}" }
                }
            }
            increaseProgress()
        }

        // Remove those that are blocked by a living item
        val itemBlockedIterator = deadItems.iterator()
        while (itemBlockedIterator.hasNext()) {
            val possibleCorpse = itemBlockedIterator.next()

            val remove = aliveItems.any { livingBlocker ->
                val isBlocked = possibleCorpse.item.isParentOf(livingBlocker.item)
                if (isBlocked) log(TAG) { "Blocked by living item: ${possibleCorpse.item} by ${livingBlocker.item}" }
                isBlocked
            }
            if (remove) itemBlockedIterator.remove()

            increaseProgress()
        }

        // Remove corpses that are already covered
        val itemCoveredIterator = deadItems.iterator()
        while (itemCoveredIterator.hasNext()) {
            val possibleNested = itemCoveredIterator.next()

            val remove = deadItems.any { corpse ->
                val isCovered = corpse.item.isParentOf(possibleNested.item)
                log(TAG) { "Covered nested corpse: ${possibleNested.item} by ${corpse.item}" }
                isCovered
            }
            if (remove) itemCoveredIterator.remove()

            increaseProgress()
        }

        if (Bugs.isDebug) {
            aliveItems.forEach { log(TAG) { "Final alive: ${it.item}" } }
            deadItems.forEach { log(TAG) { "Final dead: ${it.item}" } }
        }

        return deadItems.map { ownerInfo ->
            Corpse(
                ownerInfo = ownerInfo,
                content = ownerInfo.item.walk(gatewaySwitch).toList(),
                isWriteProtected = ownerInfo.item.canWrite(gatewaySwitch),
                riskLevel = when {
                    ownerInfo.isKeeper -> RiskLevel.USER_GENERATED
                    ownerInfo.isCommon -> RiskLevel.COMMON
                    else -> RiskLevel.NORMAL
                }
            ).also { log(TAG, INFO) { "Found Corpse: $it" } }
        }
    }

    private suspend fun findPotentialCorpses(): Collection<OwnerInfo> {
        val clutterMarkerList = clutterRepo.getMarkerForLocation(DataArea.Type.SDCARD)

        updateProgressCount(Progress.Count.Counter(0, clutterMarkerList.size))
        updateProgressSecondary(R.string.general_progress_filtering)

        val uncleaned = clutterMarkerList
            .filter { it.isPrefixFreeBasePathDirect } // Can't reverse-match regex
            .mapNotNull { marker ->
                // Get files for this marker, based on it's basepath.
                val existing = getMarkersThatExist(marker)
                if (existing.isNotEmpty()) {
                    log(TAG) { "Resolved $marker to existing $existing" }
                    marker to existing
                } else {
                    null
                }
            }
            .map { (marker, potentialCorpses) ->
                // marker + List<AreaInfo> --> areaInfo + List<Owner>
                potentialCorpses.mapNotNull { areaInfo ->
                    val match = marker.match(areaInfo.type, areaInfo.prefixFreePath) ?: return@mapNotNull null
                    areaInfo to match.packageNames.map { Owner(it, match.flags) }
                }
            }
            .flatten()
            .groupBy { it.first }
            .mapValues { (areaInfo, listOfOwnerLists) ->
                // Get unique data
                listOfOwnerLists.map { it.second }.flatten().toSet()
            }
            .map { (areaInfo, owners) ->
                OwnerInfo(
                    areaInfo = areaInfo,
                    owners = owners,
                    installedOwners = owners.filter { pkgRepo.isInstalled(it.pkgId) }.toSet(),
                    hasUnknownOwner = false
                )
            }
            .onEach { increaseProgress() }

        val cleaned = uncleaned.toSet()
        val duplicates = uncleaned - uncleaned.intersect(cleaned)
        if (duplicates.isNotEmpty()) {
            log(TAG) { "Pruned duplicate entries: $duplicates" }
        }
        return cleaned
    }

    private suspend fun getMarkersThatExist(marker: Marker): Collection<AreaInfo> {
        // TODO does caching here actually help?
        // If we have the same prefixFreeBasePath for two items one could be direct and one not.
        val cacheKey = CacheKey(marker.prefixFreeBasePath, marker.isPrefixFreeBasePathDirect)
        val cachedData = fileCache[cacheKey]
        if (cachedData != null) return cachedData

        val candidatesThatExist = mutableSetOf<APath>()
        areaManager.currentAreas()
            .filter { it.type == DataArea.Type.SDCARD }
            .map { it.path.child(marker.prefixFreeBasePath) }
            .filter { it.exists(gatewaySwitch) }
            .map { candidate ->
                // Sdcard names are case-insensitive, the marker name is fixed though..
                // Actual name could be "MiBand" but by using the marker prefix we would end up with "miband"
                (candidate as? LocalPath)?.asFile()?.parentFile
                    ?.listFiles()
                    ?.singleOrNull { candidate.path != it.path && candidate.path.lowercase() == it.path.lowercase() }
                    ?.let {
                        log(TAG, WARN) { "Correcting casing on case-insensitive match: $it" }
                        it.toLocalPath()
                    }
                    ?: candidate
            }
            .forEach { candidate ->
                // We don't add the sdcard root as candidate.
                if (marker.prefixFreeBasePath.isNotEmpty() && candidate.canRead(gatewaySwitch)) {
                    candidatesThatExist.add(candidate)
                }
                if (!marker.isPrefixFreeBasePathDirect) {
                    // <sdcard(level0|1)>/(level1|2)/(level2|3)/(level3|4)/corpse
                    val files = candidate.walk(gatewaySwitch)
                        .onEach { log(TAG, INFO) { "Walking: $it" } }
                        .toList()
                    candidatesThatExist.addAll(files)
                }
            }

        return candidatesThatExist
            .map { fileForensics.identifyArea(it) }
            .filter { it.type == DataArea.Type.SDCARD }
            .also { fileCache[cacheKey] = it }
    }

    data class CacheKey(val path: String, val direct: Boolean)

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SdcardCorpseFilter): CorpseFilter
    }

    companion object {
        val TAG: String = logTag("CorpseFinder", "Filter", "Sdcard")
    }
}