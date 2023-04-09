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
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.toLocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.exclusion.core.ExclusionManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class SdcardCorpseFilter @Inject constructor(
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val corpseFinderSettings: CorpseFinderSettings,
    private val clutterRepo: ClutterRepo,
    private val pkgRepo: PkgRepo,
    private val exclusionManager: ExclusionManager,
) : CorpseFilter(TAG, DEFAULT_PROGRESS) {

    private val fileCache: MutableMap<CacheKey, Collection<AreaInfo>> = HashMap()

    override suspend fun doScan(): Collection<Corpse> {
        log(TAG) { "Scanning..." }

        updateProgressPrimary("SDCARD")

        val result = doReverseCSI()
        fileCache.clear()
        return result
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
        updateProgressCount(Progress.Count.Percent(0, potentialCorpses.size))

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
        updateProgressCount(Progress.Count.Percent(0, deadItems.size * 2))
        val itemBlockedIterator = deadItems.iterator()
        while (itemBlockedIterator.hasNext()) {
            val possibleCorpse = itemBlockedIterator.next()

            val remove = aliveItems.any { livingBlocker ->
                val isBlocked = possibleCorpse.item.isAncestorOf(livingBlocker.item)
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
                val isCovered = corpse.item.isAncestorOf(possibleNested.item)
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

        updateProgressCount(Progress.Count.Percent(0, deadItems.size))
        return deadItems.map { ownerInfo ->
            val lookup = ownerInfo.item.lookup(gatewaySwitch)
            val content = if (lookup.isDirectory) ownerInfo.item.walk(gatewaySwitch).toSet() else emptyList()
            val corpse = Corpse(
                filterType = this::class,
                ownerInfo = ownerInfo,
                lookup = lookup,
                content = content,
                isWriteProtected = ownerInfo.item.canWrite(gatewaySwitch),
                riskLevel = when {
                    ownerInfo.isKeeper -> RiskLevel.KEEPER
                    ownerInfo.isCommon -> RiskLevel.COMMON
                    else -> RiskLevel.NORMAL
                }
            )

            increaseProgress()
            log(TAG, INFO) { "Found Corpse: $corpse" }

            corpse
        }
    }

    private suspend fun findPotentialCorpses(): Collection<OwnerInfo> {
        val clutterMarkerList = clutterRepo.getMarkerForLocation(DataArea.Type.SDCARD)

        updateProgressCount(Progress.Count.Percent(0, clutterMarkerList.size))
        updateProgressSecondary(R.string.general_progress_filtering)

        val areas = areaManager.currentAreas().filter { it.type == DataArea.Type.SDCARD }

        val uncleaned = clutterMarkerList
            .asFlow()
            .onEach { increaseProgress() }
            .filter { it.isDirectMatch } // Can't reverse-match regex
            .mapNotNull { marker ->
                // Get files for this marker, based on it's basepath.
                val existing = getMarkersThatExist(areas, marker)
                if (existing.isEmpty()) return@mapNotNull null

                log(TAG) { "Resolved $marker to existing $existing" }
                marker to existing
            }
            .map { (marker, potentialCorpses) ->
                // marker + List<AreaInfo> --> areaInfo + List<Owner>
                potentialCorpses.mapNotNull { areaInfo ->
                    val match = marker.match(areaInfo.type, areaInfo.prefixFreePath) ?: return@mapNotNull null
                    areaInfo to match.packageNames.map { Owner(it, areaInfo.userHandle, match.flags) }
                }
            }
            .toList()
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
                    installedOwners = owners.filter {
                        pkgRepo.isInstalled(it.pkgId, areaInfo.userHandle)
                    }.toSet(),
                    hasUnknownOwner = false
                )
            }


        val cleaned = uncleaned.toSet()
        val duplicates = uncleaned - uncleaned.intersect(cleaned)
        if (duplicates.isNotEmpty()) {
            log(TAG) { "Pruned duplicate entries: $duplicates" }
        }
        return cleaned
    }

    private suspend fun getMarkersThatExist(areas: Collection<DataArea>, marker: Marker): Collection<AreaInfo> {
        // If we have the same prefixFreeBasePath for two items one could be direct and one not.
        val cacheKey = CacheKey(marker.segments, marker.isDirectMatch)
        val cachedData = fileCache[cacheKey]
        if (cachedData != null) return cachedData

        val candidatesThatExist = mutableSetOf<APath>()
        areas
            .map { it to it.path.child(*marker.segments.toTypedArray()) }
            .filter { it.second.exists(gatewaySwitch) }
            .map { (area, candidate) ->
                // Sdcard names are case-insensitive, the marker name is fixed though..
                // Actual name could be "MiBand" but by using the marker prefix we would end up with "miband"
                val resolved = (candidate as? LocalPath)?.asFile()?.parentFile
                    ?.listFiles()
                    ?.singleOrNull { candidate.path != it.path && candidate.path.lowercase() == it.path.lowercase() }
                    ?.let {
                        log(TAG, WARN) { "Correcting casing on case-insensitive match: $it" }
                        it.toLocalPath()
                    }
                    ?: candidate
                area to resolved
            }
            .forEach { (area, candidate) ->
                // We don't add the sdcard root as candidate.
                if (marker.segments.isNotEmpty() && candidate.canRead(gatewaySwitch)) {
                    candidatesThatExist.add(candidate)
                }
                if (!marker.isDirectMatch) {
                    // <sdcard(level0|1)>/(level1|2)/(level2|3)/(level3|4)/corpse
                    val files = candidate.walk(
                        gatewaySwitch,
                        filter = { item -> item.segments.size <= (4 + area.path.segments.size) }
                    )
                        .onEach { log(TAG, INFO) { "Walking: $it" } }
                        .toList()
                    candidatesThatExist.addAll(files.map { it.lookedUp })
                }
            }

        return candidatesThatExist
            .mapNotNull { fileForensics.identifyArea(it) }
            .filter { it.type == DataArea.Type.SDCARD }
            .also { fileCache[cacheKey] = it }
    }

    data class CacheKey(val path: List<String>, val direct: Boolean)

    @Reusable
    class Factory @Inject constructor(
        private val settings: CorpseFinderSettings,
        private val filterProvider: Provider<SdcardCorpseFilter>
    ) : CorpseFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterSdcardEnabled.value()
        override suspend fun create(): CorpseFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): CorpseFilter.Factory
    }

    companion object {
        val DEFAULT_PROGRESS = Progress.Data(
            primary = R.string.corpsefinder_filter_sdcard_summary.toCaString(),
            secondary = R.string.general_progress_loading.toCaString(),
            count = Progress.Count.Indeterminate()
        )
        val TAG: String = logTag("CorpseFinder", "Filter", "Sdcard")
    }
}