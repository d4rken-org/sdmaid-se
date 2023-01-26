package eu.darken.sdmse.appcleaner.core.scanner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.clutter.hasFlags
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.*
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.*
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.exclusions.core.Exclusion
import eu.darken.sdmse.exclusions.core.ExclusionManager
import eu.darken.sdmse.exclusions.core.currentExclusions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import okio.IOException
import java.time.Instant
import javax.inject.Inject
import kotlin.reflect.KClass


class AppScanner @Inject constructor(
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val filterFactories: Set<@JvmSuppressWildcards ExpendablesFilter.Factory>,
    private val postProcessorModule: PostProcessorModule,
    @ApplicationContext private val context: Context,
    private val fileForensics: FileForensics,
    private val rootManager: RootManager,
    private val exclusionManager: ExclusionManager,
    private val pkgRepo: PkgRepo,
    private val clutterRepo: ClutterRepo,
    private val settings: AppCleanerSettings,
    private val inaccessibleCacheProvider: InaccessibleCacheProvider,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.DEFAULT_STATE.copy(
            primary = R.string.general_progress_preparing.toCaString()
        )
    )
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private lateinit var enabledFilters: Collection<ExpendablesFilter>
    private var isRooted: Boolean = false

    suspend fun initialize() {
        log(TAG, VERBOSE) { "initialize()" }
        enabledFilters = filterFactories
            .filter { it.isEnabled() }
            .map { it.create() }
            .onEach { it.initialize() }
            .onEach { log(TAG, VERBOSE) { "Filter enabled: $it" } }
        log(TAG) { "${enabledFilters.size} filter are enabled" }
        isRooted = rootManager.isRooted()
    }

    suspend fun scan(
        pkgFilter: Collection<Pkg.Id> = emptySet()
    ): Collection<AppJunk> {
        log(TAG, INFO) { "scan(pkgFilter=$pkgFilter)" }
        updateProgressPrimary(R.string.general_progress_preparing)
        updateProgressCount(Progress.Count.Indeterminate())

        if (enabledFilters.isEmpty()) {
            log(TAG, WARN) { "0 enabled filter !?" }
            return emptySet()
        }

        val includeSystemApps = settings.includeSystemAppsEnabled.value()
        val includeRunningApps = settings.includeRunningAppsEnabled.value()

        updateProgressSecondary(R.string.general_progress_loading_app_data)
        val allCurrentPkgs = pkgRepo.currentPkgs()
            .filter { includeSystemApps || !it.isSystemApp }
            .filter { pkgFilter.isEmpty() || pkgFilter.contains(it.id) }
            .filter { it.id.name != context.packageName }

        log(TAG) { "${allCurrentPkgs.size} apps to check :)" }

        val searchPathMap = buildSearchMap(allCurrentPkgs)
        searchPathMap.forEach { log(TAG) { "Searchmap contains ${it.value.size} pathes for ${it.key}." } }

        val expendablesFromAppData = readAppDirs(searchPathMap)

        val inaccessibleCaches = determineInaccessibleCaches(allCurrentPkgs)

        val appJunks = allCurrentPkgs.mapNotNull { pkg ->
            val expendables = expendablesFromAppData[pkg.id]
            val inaccessibleCache = inaccessibleCaches.firstOrNull { pkg.id == it.pkgId }
            if (expendables.isNullOrEmpty() && inaccessibleCache == null) return@mapNotNull null

            val byFilterType: Map<KClass<out ExpendablesFilter>, Collection<APathLookup<*>>>? = expendables
                ?.groupBy { it.type }
                ?.mapValues { matches -> matches.value.map { it.file } }

            AppJunk(
                pkg = pkg,
                expendables = byFilterType,
                inaccessibleCache = inaccessibleCache,
            )
        }

        log(TAG) { "Found ${expendablesFromAppData.size} apps with expendable files" }

        updateProgressPrimary(R.string.general_progress_filtering)
        updateProgressCount(Progress.Count.Indeterminate())

        val prunedAppJunks = postProcessorModule.postProcess(appJunks)
        log(TAG, INFO) { "After purging empties we have ${prunedAppJunks.size} apps." }

        prunedAppJunks.forEach { appJunk ->
            appJunk.inaccessibleCache?.let { log(TAG, INFO) { "AppJunk-${appJunk.pkg.id}: Inaccessible: $it" } }
            appJunk.expendables?.forEach {
                log(TAG, INFO) { "AppJunk-${appJunk.pkg.id}: ${it.key.simpleName} with ${it.value.size} items" }
            }
        }
        log(TAG, INFO) { "${prunedAppJunks.sumOf { it.size }} bytes can be freed across ${prunedAppJunks.size} apps" }

        return prunedAppJunks
    }

    private suspend fun buildSearchMap(
        pkgs: Collection<Installed>,
    ): Map<AreaInfo, Collection<Pkg.Id>> {
        updateProgressSecondary(R.string.general_progress_loading_data_areas)
        updateProgressCount(Progress.Count.Indeterminate())

        val dataAreaMap = getDataAreaMap()

        val pkgExclusions = exclusionManager.currentExclusions()
            .filter { it.tags.contains(Exclusion.Tag.APPCLEANER) || it.tags.contains(Exclusion.Tag.GENERAL) }
            .filterIsInstance<Exclusion.Package>()
        val includeSystemApps = settings.includeSystemAppsEnabled.value()

        val searchPathMap = mutableMapOf<AreaInfo, Collection<Pkg.Id>>()

        updateProgressPrimary(R.string.general_progress_generating_searchpaths)

        for (pkg in pkgs) {
            updateProgressSecondary(pkg.label?.get(context) ?: pkg.packageName)
            updateProgressCount(Progress.Count.Percent(0, pkgs.size))

            log(TAG) { "Generating search paths for ${pkg.packageName}" }

            if (!includeSystemApps && pkg.isSystemApp) continue
            if (pkgExclusions.any { it.match(pkg.id) }) {
                log(TAG, INFO) { "Excluded package: ${pkg.id}" }
                continue
            }

            val interestingPaths = mutableSetOf<AreaInfo>()

            pkg.packageInfo.applicationInfo
                ?.dataDir
                ?.takeIf { it.isNotEmpty() }
                ?.let { dataDir ->
                    if (isRooted) {
                        fileForensics.identifyArea(LocalPath.build(dataDir))?.let {
                            interestingPaths.add(it)
                        }
                    } else {
                        log(TAG) { "Skipping $dataDir (no root)" }
                    }
                }


            val clutterMarkerForPkg = clutterRepo.getMarkerForPkg(pkg.id)

            listOf(
                DataArea.Type.PRIVATE_DATA,
                DataArea.Type.PUBLIC_DATA,
                DataArea.Type.PUBLIC_MEDIA,
                DataArea.Type.SDCARD
            )
                .mapNotNull { dataAreaMap[it] }
                .flatten()
                .forEach { tlCandidate ->
                    if (tlCandidate.type != DataArea.Type.SDCARD && tlCandidate.file.name == pkg.packageName) {
                        interestingPaths.add(tlCandidate)
                        return@forEach
                    }

                    clutterMarkerForPkg
                        .mapNotNull { it.match(tlCandidate.type, tlCandidate.prefixFreePath) }
                        .firstOrNull { !it.hasFlags(Marker.Flag.CUSTODIAN) }
                        ?.let {
                            interestingPaths.add(tlCandidate)
                        }
                }

            // Check all files on public storage for potentially deeper nested clutter
            dataAreaMap
                .mapNotNull { dataAreaMap[DataArea.Type.SDCARD] }
                .flatten()
                .forEach { topLvlSDDir ->
                    clutterMarkerForPkg
                        .filter { it.areaType == DataArea.Type.SDCARD }
                        .filter { !it.hasFlags(Marker.Flag.CUSTODIAN) }
                        .filter { it.isDirectMatch }
                        .filter { it.segments.startsWith(topLvlSDDir.prefixFreePath, ignoreCase = true) }
                        .map { topLvlSDDir.prefix.child(*it.segments.toTypedArray()) }
                        .filter { it.exists(gatewaySwitch) }
                        .forEach { file ->
                            log(TAG) { "Nested marker target exists: $file" }
                            fileForensics.identifyArea(file)?.let {
                                interestingPaths.add(it)
                            }
                        }
                }


            // To build the search map we find all pathes that belong to an app: One App multiple pathes
            // Then we reverse the mapping here as each location can have multiple owners: One path, multiple apps
            // In the next step we will attribute each location to a single owner
            for (path in interestingPaths) {
                searchPathMap[path] = (searchPathMap[path] ?: emptySet()).plus(pkg.id)
            }

            increaseProgress()
        }

        log(TAG) { "Search paths build (${searchPathMap.keys.size} interesting paths)." }

        return searchPathMap
    }

    /**
     * First we determine all areas that we check,
     * i.e. public/priv storage and some levels of the root of the sdcard
     */
    private suspend fun getDataAreaMap(): Map<DataArea.Type, Collection<AreaInfo>> {
        val currentAreas = areaManager.currentAreas()

        val pathExclusions = exclusionManager.currentExclusions()
            .filter { it.tags.contains(Exclusion.Tag.APPCLEANER) || it.tags.contains(Exclusion.Tag.GENERAL) }
            .filterIsInstance<Exclusion.Path>()

        val areaDataMap = mutableMapOf<DataArea.Type, Collection<AreaInfo>>()

        // TODO do we need this? without root, the data area isn't supplied by the DataAreaManager?
        if (isRooted) {
            areaDataMap[DataArea.Type.PRIVATE_DATA] = emptySet()
            currentAreas
                .filter { it.type == DataArea.Type.PRIVATE_DATA }
                .forEach { area ->
                    val areaLookups = try {
                        area.path.lookupFiles(gatewaySwitch)
                    } catch (e: IOException) {
                        log(TAG, ERROR) { "Failed to lookup $area: ${e.asLog()}" }
                        return@forEach
                    }
                    val areaInfos = areaLookups
                        .filter { it.fileType == FileType.DIRECTORY }
                        .filter { appDir ->
                            pathExclusions.none { it.match(appDir) }.also {
                                if (!it) log(TAG, INFO) { "Excluded during PRIVATE_DATA scan: $appDir" }
                            }
                        }
                        .mapNotNull { lookup ->
                            fileForensics.identifyArea(lookup).also {
                                if (it == null) log(TAG, WARN) { "Failed to identify $it" }
                            }
                        }

                    areaDataMap[area.type] = when {
                        areaDataMap[area.type] == null -> areaInfos
                        else -> areaDataMap[area.type]!!.plus(areaInfos)
                    }
                }
        }

        currentAreas
            .filter {
                listOf(
                    DataArea.Type.PUBLIC_DATA,
                    DataArea.Type.SDCARD,
                    DataArea.Type.PUBLIC_MEDIA
                ).contains(it.type)
            }
            .forEach { area ->
                val areaLookups = try {
                    area.path.lookupFiles(gatewaySwitch)
                } catch (e: IOException) {
                    log(TAG, ERROR) { "Failed to lookup $area: ${e.asLog()}" }
                    return@forEach
                }

                val areaInfos = areaLookups
                    .filter { it.fileType == FileType.DIRECTORY }
                    .mapNotNull { lookup ->
                        fileForensics.identifyArea(lookup).also {
                            if (it == null) log(TAG, WARN) { "Failed to identify $it" }
                        }
                    }
                    .filter { areaInfo ->
                        val excluded = pathExclusions.any { it.match(areaInfo.file) }
                        val edgeCase = !isRooted && area.type == DataArea.Type.PUBLIC_DATA
                                && areaInfo.prefixFreePath.size >= 2
                                && areaInfo.prefixFreePath[1] == "cache"
                        if (excluded && edgeCase) {
                            log(TAG, WARN) { "Exclusion skipped to do default cache coverage: $areaInfo" }
                        } else if (excluded) {
                            log(TAG, WARN) { "Excluded during public area scan: $areaInfo" }
                        }
                        !excluded || edgeCase
                    }

                areaDataMap[area.type] = when {
                    areaDataMap[area.type] == null -> areaInfos
                    else -> areaDataMap[area.type]!!.plus(areaInfos)
                }
            }

        return areaDataMap
    }

    data class FilterMatch(
        val file: APathLookup<*>,
        val type: KClass<out ExpendablesFilter>,
    )

    private suspend fun readAppDirs(searchPathsOfInterest: Map<AreaInfo, Collection<Pkg.Id>>): Map<Pkg.Id, Collection<FilterMatch>> {
        updateProgressPrimary(R.string.general_progress_searching)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Percent(0, searchPathsOfInterest.size))

        val minCacheAgeMs = settings.minCacheAgeMs.value()
        val minCacheSizeBytes = settings.minCacheSizeBytes.value()

        val results = HashMap<Pkg.Id, Collection<FilterMatch>>()

        searchPathsOfInterest.entries.forEach spoi@{ target ->
            log(TAG, VERBOSE) { "Searching ${target.key.file} (${target.value})" }
            updateProgressSecondary(target.key.file.userReadablePath)

            val spoi = target.key
            val possibleOwners = target.value

            val pathContents = try {
                spoi.file
                    .walk(
                        gatewaySwitch,
                        filter = {
                            when {
                                it.path.contains("/org.winehq.wine/files/prefix") -> false
                                it.path.contains("/.wine/") -> false
                                else -> true
                            }
                        }
                    )
                    .toList()
            } catch (e: IOException) {
                log(TAG, WARN) { "Failed to read ${spoi.file}: ${e.asLog()}" }
                emptyList()
            }

            pathContents
                .filter { minCacheAgeMs == 0L || it.modifiedAt >= Instant.now().minusMillis(minCacheAgeMs) }
                .filter { minCacheSizeBytes == 0L || it.size > minCacheSizeBytes }
                .filter { it.segments.startsWith(spoi.file.segments) }
                .forEach { foi ->
                    // TODO do we need this extra lookup or can we construct the prefix free segments without it?
                    val foiAreaInfo = fileForensics.identifyArea(foi)
                    if (foiAreaInfo == null) {
                        log(TAG, WARN) { "Failed to identify $foi" }
                        return@forEach
                    }
                    for (pkgId in possibleOwners) {
                        val type: KClass<out ExpendablesFilter> = enabledFilters
                            .firstOrNull { it.isExpendable(pkgId, foi, spoi.type, foiAreaInfo.prefixFreePath) }
                            ?.javaClass?.kotlin
                            ?: continue

                        log(TAG, INFO) { "${type.simpleName} matched ${spoi.type}:${foiAreaInfo.prefixFreePath}" }

                        results[pkgId] = (results[pkgId] ?: emptySet()).plus(FilterMatch(foi, type))
                    }

                }

            increaseProgress()
        }

        return results
    }

    private suspend fun determineInaccessibleCaches(
        pkgs: Collection<Installed>,
    ): Collection<InaccessibleCache> {
        if (!settings.includeInaccessibleEnabled.value() || rootManager.isRooted()) return emptyList()
        val acsEnabled = settings.useAccessibilityService.value()
        val isSamsungRom = BuildWrap.MANUFACTOR == "Samsung"
        return pkgs
            .filter { pkg ->
                // On Samsung ROMs, we can't open the settings page for disabled apps
                if (!isSamsungRom) return@filter true
                // TODO Is this our concern? Or should this be up to deletion, not the scan?
                (!acsEnabled || pkg.isEnabled).also {
                    if (!it) log(TAG, WARN) { "Skipping inaccessible cache for $pkg. Package disabled, ACS enabled." }
                }
            }
            .filterIsInstance<NormalPkg>()
            .mapNotNull {
                inaccessibleCacheProvider.getCacheFile(it.id)
            }
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Scanner")
    }
}
