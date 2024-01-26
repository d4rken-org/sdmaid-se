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
) : CorpseFilter(TAG, Progress.Data(primary = R.string.corpsefinder_filter_sdcard_summary.toCaString())) {

    private val markerThatExistCache: MutableMap<CacheKey, Collection<AreaInfo>> = HashMap()

    override suspend fun doScan(): Collection<Corpse> {
        log(TAG) { "Scanning..." }

        updateProgressPrimary(R.string.corpsefinder_filter_sdcard_label)
        updateProgressCount(Progress.Count.Indeterminate())

        val result = try {
            val potentialCorpses = findPotentialCorpses()
            log(TAG) { "Got ${potentialCorpses.size} potential corpses to check" }

            checkPotentialCorpses(potentialCorpses)
        } finally {
            markerThatExistCache.clear()
        }
        return result
    }

    /**
     * We basically do a reverse CSI.
     * Instead of looking who owns a path, we look at all owners we know, and see if any exists.
     * This works because SDCARD is not a blacklist location, items have to be whitelisted via clutterdb.
     * To improve performance, we only do this for the nested corpses, and not the top level,
     * as there are usually more toplevel markers than actual toplevel folders/files.
     */
    private suspend fun findPotentialCorpses(): Collection<OwnerInfo> {
        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressCount(Progress.Count.Indeterminate())

        val areas = areaManager.currentAreas().filter { it.type == DataArea.Type.SDCARD }

        val topLevelContent = areas.associateWith { area ->
            updateProgressSecondary {
                it.getString(
                    eu.darken.sdmse.common.R.string.general_progress_processing_x,
                    area.path.userReadablePath.get(it)
                )
            }
            updateProgressCount(Progress.Count.Indeterminate())
            val content = area.path.listFiles(gatewaySwitch)

            updateProgressCount(Progress.Count.Percent(content.size))
            content.map {
                fileForensics.findOwners(it)!!.also {
                    increaseProgress()
                }
            }
        }

        val relevantMarkers = clutterRepo
            .getMarkerForLocation(DataArea.Type.SDCARD)
            .filter { it.isDirectMatch } // Can't reverse-match regex
            .filter { it.segments.size > 1 } // We already looked up the top level content

        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        updateProgressCount(Progress.Count.Percent(relevantMarkers.size))

        val nestedUncleaned = relevantMarkers
            .asFlow()
            .map { marker ->
                // Get files for this marker, based on it's basepath.
                val existing = areas
                    .filter { area ->
                        // Only makes sense to process this nested marker if the parent actually exists
                        val areaContent = topLevelContent[area]!!
                        areaContent.any { it.item.segments.isAncestorOf(marker.segments) }
                    }
                    .map { determineNestedCandidates(it, marker) }
                    .flatten()

                marker to existing
            }
            .onEach { increaseProgress() }
            .filter { it.second.isNotEmpty() }
            .map { (marker, potentialCorpses) ->
                log(TAG) { "Resolved $marker to existing $potentialCorpses" }
                // marker + List<AreaInfo> --> areaInfo + List<Owner>
                potentialCorpses.mapNotNull { areaInfo ->
                    val match = marker.match(areaInfo.type, areaInfo.prefixFreeSegments) ?: return@mapNotNull null
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

        // Multiple marker for the same path is possible, clean up dubs
        val nestedCleaned = nestedUncleaned.toSet()
        val duplicates = nestedUncleaned - nestedUncleaned.intersect(nestedCleaned)
        if (duplicates.isNotEmpty()) log(TAG) { "Pruned duplicate entries: $duplicates" }

        return topLevelContent.values.flatten() + nestedCleaned
    }

    private suspend fun determineNestedCandidates(area: DataArea, marker: Marker): Collection<AreaInfo> {
        val cacheKey = CacheKey(
            area = area,
            path = marker.segments,
            // If we have the same prefixFreeBasePath for two items one could be direct and one not.
            direct = marker.isDirectMatch
        )
        val cachedData = markerThatExistCache[cacheKey]
        if (cachedData != null) return cachedData

        val candidateRaw = area.path.child(*marker.segments.toTypedArray())
        if (!candidateRaw.exists(gatewaySwitch)) return emptyList()

        // Sdcard names are case-insensitive, the marker name is fixed though..
        // Actual name could be "MiBand" but by using the marker prefix we would end up with "miband"
        val candidateResolved = (candidateRaw as? LocalPath)?.asFile()?.parentFile
            ?.listFiles()
            ?.singleOrNull { candidateRaw.path != it.path && candidateRaw.path.lowercase() == it.path.lowercase() }
            ?.let {
                log(TAG, WARN) { "Correcting casing on case-insensitive match: $it" }
                it.toLocalPath()
            }
            ?: candidateRaw

        val candidateExisting = mutableSetOf<APath>()

        // We don't add the sdcard root as candidate.
        if (marker.segments.isNotEmpty() && candidateResolved.canRead(gatewaySwitch)) {
            candidateExisting.add(candidateResolved)
        }
        if (!marker.isDirectMatch) {
            // <sdcard(level0|1)>/(level1|2)/(level2|3)/(level3|4)/corpse
            val files = candidateResolved.walk(
                gatewaySwitch,
                options = APathGateway.WalkOptions(
                    onFilter = { item -> item.segments.size <= (4 + area.path.segments.size) }
                )
            )
                .onEach { log(TAG, INFO) { "Walking: $it" } }
                .toList()
            candidateExisting.addAll(files.map { it.lookedUp })
        }

        return candidateExisting
            .mapNotNull { fileForensics.identifyArea(it) }
            .filter { it.type == DataArea.Type.SDCARD }
            .also { markerThatExistCache[cacheKey] = it }
    }

    data class CacheKey(
        val area: DataArea,
        val path: List<String>,
        val direct: Boolean
    )

    private suspend fun checkPotentialCorpses(potentialCorpses: Collection<OwnerInfo>): Collection<Corpse> {
        // Deep search each possible corpse
        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        updateProgressCount(Progress.Count.Percent(potentialCorpses.size))

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
        updateProgressCount(Progress.Count.Percent(deadItems.size * 2))
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

        updateProgressCount(Progress.Count.Percent(deadItems.size))
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
        val TAG: String = logTag("CorpseFinder", "Filter", "Sdcard")
    }
}