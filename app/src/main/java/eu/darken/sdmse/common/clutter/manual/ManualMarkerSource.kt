package eu.darken.sdmse.common.clutter.manual

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.clutter.MarkerSource
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.currentPkgs
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class ManualMarkerSource(
    private val pkgRepo: PkgRepo,
    private val clutterEntriesProvider: () -> Collection<JsonMarkerGroup>
) : MarkerSource {

    private val dataCacheLock = Mutex()
    private var dataCache: Map<Pkg.Id, Collection<ManualMarker>>? = null
    private suspend fun getCachedMarkerMap(): Map<Pkg.Id, Collection<ManualMarker>> = dataCacheLock.withLock {
        dataCache?.let { return@withLock it }
        log(TAG) { "Generating marker map..." }
        buildDatabase(clutterEntriesProvider()).also { dataCache = it }
    }

    private val locationCacheLock = Mutex()
    private val locationCache = mutableMapOf<DataArea.Type, Collection<Marker>>()

    override suspend fun getMarkerForLocation(
        areaType: DataArea.Type
    ): Set<Marker> = locationCacheLock.withLock {

        val markers = locationCache[areaType]
        if (markers != null) return@withLock markers.toSet()

        val start = System.currentTimeMillis()

        val results = mutableSetOf<Marker>()
        val manualMarkers = getCachedMarkerMap().values
        for (markersPerPkg in manualMarkers) {
            for (marker in markersPerPkg) {
                if (marker.areaType == areaType) {
                    results.add(marker)
                }
            }
        }
        locationCache[areaType] = results

        val stop = System.currentTimeMillis()
        log(TAG) { "Generated cached marker data for $areaType in ${stop - start}ms, size ${results.size}" }
        results
    }

    override suspend fun match(areaType: DataArea.Type, prefixFreeBasePath: List<String>): Set<Marker.Match> {
        val result = mutableSetOf<Marker.Match>()
        for (marker in getMarkerForLocation(areaType)) {
            val match = marker.match(areaType, prefixFreeBasePath)
            if (match != null) result.add(match)
        }
        return result
    }

    override suspend fun getMarkerForPkg(pkgId: Pkg.Id): Set<Marker> {
        val result = mutableSetOf<Marker>()
        val markers: Collection<ManualMarker>? = getCachedMarkerMap()[pkgId]
        if (markers != null) result.addAll(markers)
        return result
    }

    private suspend fun buildDatabase(
        clutterEntries: Collection<JsonMarkerGroup>
    ): Map<Pkg.Id, MutableCollection<ManualMarker>> {
        log(TAG, VERBOSE) { "buildDatabase(entries=${clutterEntries.size})..." }

        val startTimeMarkerGeneration = System.currentTimeMillis()
        val installedApps: Collection<Installed> = pkgRepo.currentPkgs()
        var markerCount: Long = 0
        val clutterMap: HashMap<Pkg.Id, MutableCollection<ManualMarker>> = LinkedHashMap()
        var counter = 0
        clutterEntries.forEach { entry ->
            counter++

            require(!((entry.pkgs == null || entry.pkgs.isEmpty()) && (entry.regexPkgs == null || entry.regexPkgs.size < 0))) { "Invalid marker: No pkgs defined #$counter:$entry" }
            require(entry.mrks.isNotEmpty()) { "Invalid marker: No markers defined #$counter:$entry" }

            val rawPkgs = mutableSetOf<String>()
            if (entry.pkgs != null) {
                for (pkg in entry.pkgs) {
                    if (!rawPkgs.add(pkg)) {
                        log(TAG, WARN) { "Package defined multiple times: $pkg" }
                    }
                }
            }
            if (entry.regexPkgs != null) {
                for (regexPkg in entry.regexPkgs) {
                    val pattern = Regex(regexPkg)
                    for (app in installedApps) {
                        if (pattern.matches(app.packageName)) {
                            log(TAG, VERBOSE) { "Regex package match: pkg=${app.packageName}, entry=$entry" }
                            if (!rawPkgs.add(app.packageName)) {
                                log(TAG, WARN) { "Package defined multiple times: ${app.packageName}" }
                            }
                        }
                    }
                }
                // If we found no installed app matches the regex, we still need to add the marker to be able to detect this as corpse.
                // We add the regexes as packages, but theoretically random data would work too
                if (rawPkgs.isEmpty()) rawPkgs.addAll(entry.regexPkgs)
            }
            val markerPkgs = rawPkgs.map { it.toPkgId() }.toSet()
            val markerSet: MutableCollection<ManualMarker> = HashSet()
            for (raw in entry.mrks) {
                val newMarker = ManualMarker(
                    markerPkgs,
                    raw.areaType,
                    raw.path?.split("/"),
                    raw.contains,
                    raw.regex,
                    raw.flags ?: emptySet()
                )

                if (markerSet.contains(newMarker)) {
                    log(TAG, WARN) { "Duplicate marker: $newMarker" }
                }

                markerSet.add(newMarker)
                markerCount++
            }
            for (pkg in markerPkgs) {
                if (clutterMap.containsKey(pkg)) {
                    log(TAG, WARN) {
                        "Package '$pkg' is defined multiple times:\nMerging:\n$markerSet\ninto:\n${clutterMap[pkg]}"
                    }
                    clutterMap[pkg]!!.addAll(markerSet)
                } else {
                    clutterMap[pkg] = markerSet
                }
            }
        }

        val stopTimeMarkerGeneration = System.currentTimeMillis()
        log(TAG) {
            "Marker data (${clutterMap.size} pkgs, $markerCount markers) generated in ${stopTimeMarkerGeneration - startTimeMarkerGeneration}ms"
        }
        return clutterMap
    }

    companion object {
        val TAG: String = logTag("ManualMarkerSource")
    }
}