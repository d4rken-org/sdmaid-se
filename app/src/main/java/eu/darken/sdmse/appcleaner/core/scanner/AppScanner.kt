package eu.darken.sdmse.appcleaner.core.scanner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilterIdentifier
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.exists
import eu.darken.sdmse.common.files.listFiles
import eu.darken.sdmse.common.files.lookupFiles
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.common.files.startsWith
import eu.darken.sdmse.common.files.walk
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.identifyArea
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.currentPkgs
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.getPrivateDataDirs
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.pathExclusions
import eu.darken.sdmse.exclusion.core.pkgExclusions
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import okio.IOException
import java.time.Instant
import javax.inject.Inject


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
    private val userManager: UserManager2,
    private val pkgOps: PkgOps,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.Data(primary = eu.darken.sdmse.common.R.string.general_progress_preparing.toCaString())
    )
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private lateinit var enabledFilters: Collection<ExpendablesFilter>

    suspend fun initialize() {
        log(TAG, VERBOSE) { "initialize()" }
        enabledFilters = filterFactories
            .filter { it.isEnabled() }
            .map { it.create() }
            .onEach { it.initialize() }
            .onEach { log(TAG, VERBOSE) { "Filter enabled: $it" } }
        log(TAG) { "${enabledFilters.size} filter are enabled" }
    }

    suspend fun scan(
        pkgFilter: Collection<Pkg.Id> = emptySet()
    ): Collection<AppJunk> {
        log(TAG, INFO) { "scan(pkgFilter=$pkgFilter)" }
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_preparing)
        updateProgressCount(Progress.Count.Indeterminate())

        if (enabledFilters.isEmpty()) {
            log(TAG, WARN) { "0 enabled filter !?" }
            return emptySet()
        }

        val includeSystemApps = settings.includeSystemAppsEnabled.value()
        val includeRunningApps = settings.includeRunningAppsEnabled.value()
        val includeOtherUsers = settings.includeOtherUsersEnabled.value()

        val pkgExclusions = exclusionManager.pkgExclusions(SDMTool.Type.APPCLEANER)

        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_loading_app_data)

        val currentUser = userManager.currentUser()
        val allUsers = userManager.allUsers()
        val allCurrentPkgs = pkgRepo.currentPkgs()
            .filter { includeOtherUsers || it.userHandle == currentUser.handle }
            .filter { includeSystemApps || !it.isSystemApp }
            .filter { includeRunningApps || !pkgOps.isRunning(it.installId) }
            .filter { pkgFilter.isEmpty() || pkgFilter.contains(it.id) }
            .filter { pkg ->
                val isExcluded = pkgExclusions.any { it.match(pkg.id) }
                if (isExcluded) log(TAG, INFO) { "Excluded package: ${pkg.id}" }
                !isExcluded
            }
            .filter { it.id.name != context.packageName }

        log(TAG) { "${allCurrentPkgs.size} apps to check :)" }

        val expendablesFromAppData: Map<Installed.InstallId, Collection<ExpendablesFilter.Match>> =
            buildSearchMap(allCurrentPkgs)
                .onEach { log(TAG) { "Searchmap contains ${it.value.size} pathes for ${it.key}." } }
                .let { readAppDirs(it) }

        val inaccessibleCaches = determineInaccessibleCaches(allCurrentPkgs)

        val appJunks = allCurrentPkgs.mapNotNull { pkg ->
            val expendables = expendablesFromAppData[pkg.installId]
            var inaccessible = inaccessibleCaches.firstOrNull { pkg.installId == it.identifier }
            if (expendables.isNullOrEmpty() && inaccessible == null) return@mapNotNull null

            val byFilterType: Map<ExpendablesFilterIdentifier, Collection<ExpendablesFilter.Match>>? =
                expendables?.groupBy { it.identifier }

            // For <API31 we can improve accuracy manually
            if (inaccessible != null && byFilterType != null && inaccessible.externalCacheBytes == null) {
                inaccessible = inaccessible.copy(
                    externalCacheBytes = byFilterType[DefaultCachesPublicFilter::class]?.sumOf { it.expectedGain }
                )
                log(TAG) { "Guesstimated external cache size as ${inaccessible.externalCacheBytes}" }
            }

            AppJunk(
                pkg = pkg,
                userProfile = if (includeOtherUsers) allUsers.singleOrNull { it.handle == pkg.userHandle } else null,
                expendables = byFilterType,
                inaccessibleCache = inaccessible,
            )
        }

        log(TAG) { "Found ${expendablesFromAppData.size} apps with expendable files" }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_filtering)
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
        pkgsToCheck: Collection<Installed>,
    ): Map<AreaInfo, Collection<Installed.InstallId>> {
        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_loading_data_areas)
        updateProgressCount(Progress.Count.Indeterminate())

        val currentAreas = areaManager.currentAreas()
        val dataAreaMap = createDataAreaMap(currentAreas)

        val searchPathMap = mutableMapOf<AreaInfo, Collection<Installed.InstallId>>()
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_generating_searchpaths)
        updateProgressCount(Progress.Count.Percent(pkgsToCheck.size))

        for (pkg in pkgsToCheck) {
            updateProgressSecondary(pkg.label?.get(context) ?: pkg.packageName)
            log(TAG) { "Generating search paths for ${pkg.installId}" }

            val interestingPaths = mutableSetOf<AreaInfo>()

            // Private data should only be available in currentAreas if we have root
            currentAreas
                .filter { it.type == DataArea.Type.PRIVATE_DATA }
                .takeIf { it.isNotEmpty() }
                ?.let { pkg.getPrivateDataDirs(it) }
                ?.filter { it.exists(gatewaySwitch) }
                ?.map { fileForensics.identifyArea(it)!! }
                ?.forEach { interestingPaths.add(it) }

            val clutterMarkerForPkg = clutterRepo.getMarkerForPkg(pkg.id)

            dataAreaMap.values
                .flatten()
                .forEach { candidate ->
                    // Is it a direct match? Should be valid for PUBLIC_* (i.e. blacklist areas)
                    if (candidate.type != DataArea.Type.SDCARD && candidate.file.name == pkg.packageName) {
                        interestingPaths.add(candidate)
                        return@forEach
                    }

                    // Maybe an outlier that can be mapped via clutter db?
                    val indirectMatch = clutterMarkerForPkg
                        .filter { it.areaType == candidate.type }
                        .filter { !it.hasFlags(Marker.Flag.CUSTODIAN) }
                        .any { it.match(candidate.type, candidate.prefixFreeSegments) != null }
                    if (indirectMatch) interestingPaths.add(candidate)
                }

            // Check all files on public (whitelist) storage (i.e. SDCARD) for potentially deeper nested clutter
            dataAreaMap[DataArea.Type.SDCARD]
                ?.forEach { topLevelArea ->
                    clutterMarkerForPkg
                        .filter { it.areaType == topLevelArea.type }
                        .filter { !it.hasFlags(Marker.Flag.CUSTODIAN) }
                        .filter { it.isDirectMatch } // Can't reverse lookup regex markers
                        .filter { it.segments.startsWith(topLevelArea.prefixFreeSegments, ignoreCase = true) }
                        .map { topLevelArea.prefix.child(*it.segments.toTypedArray()) }
                        .filter { it.exists(gatewaySwitch) }
                        .onEach { log(TAG) { "Nested marker target exists: $it" } }
                        .mapNotNull { fileForensics.identifyArea(it) }
                        .forEach { interestingPaths.add(it) }
                }

            // To build the search map we find all pathes that belong to an app: One App multiple pathes
            // Then we reverse the mapping here as each location can have multiple owners: One path, multiple apps
            // In the next step we will attribute each location to a single owner
            for (path in interestingPaths) {
                searchPathMap[path] = (searchPathMap[path] ?: emptySet()).plus(pkg.installId)
            }

            increaseProgress()
        }

        // So far we have default blacklist entries and matches from direct clutter markers
        // We are missing dynamic markers especially from blacklist locations like PUBLIC_DATA.
        // i.e. Android/data/_some.pkg or Android/data/-some.pkg is dynamically matched to some.pkg by CSI
        dataAreaMap.entries
            .map { it.value }
            .flatten()
            .mapNotNull { fileForensics.findOwners(it) }
            .forEach { ownerInfo ->
                val installIds = ownerInfo.installedOwners
                    .filter { !it.hasFlag(Marker.Flag.CUSTODIAN) }
                    .filter { installedOwner ->
                        // The app may be installed, but also excluded from scan
                        pkgsToCheck.any { installedOwner.installId == it.installId }.also {
                            if (!it) log(TAG) { "$installedOwner is excluded " }
                        }
                    }
                    .map { it.installId }
                    .takeIf { it.isNotEmpty() }

                if (installIds == null) {
                    log(TAG) { "${ownerInfo.item} has no valid owners, excluding from search map... ${ownerInfo.installedOwners}" }
                    return@forEach
                }

                searchPathMap[ownerInfo.areaInfo] = (searchPathMap[ownerInfo.areaInfo] ?: emptySet()).plus(installIds)
            }

        log(TAG) { "Search paths build (${searchPathMap.keys.size} interesting paths)." }

        return searchPathMap
    }

    /**
     * First we determine all areas that we check,
     * i.e. public/priv storage and some levels of the root of the sdcard
     */
    private suspend fun createDataAreaMap(currentAreas: Collection<DataArea>): Map<DataArea.Type, Collection<AreaInfo>> {
        val pathExclusions = exclusionManager.pathExclusions(SDMTool.Type.APPCLEANER)

        val areaDataMap = mutableMapOf<DataArea.Type, Collection<AreaInfo>>()

        val supportedPrivateAreas = setOf(DataArea.Type.PRIVATE_DATA)

        currentAreas
            .filter { supportedPrivateAreas.contains(it.type) }
            .mapNotNull { area ->
                try {
                    area to area.path.listFiles(gatewaySwitch)
                } catch (e: IOException) {
                    log(TAG, ERROR) { "Failed to lookup $area: ${e.asLog()}" }
                    null
                }
            }
            .map { (area, content) ->
                area.type to content
                    .filter { path ->
                        val isExcluded = pathExclusions.any { it.match(path) }
                        if (isExcluded) log(TAG, INFO) { "Excluded during PRIVATE_DATA scan: $path" }
                        !isExcluded
                    }
                    .mapNotNull { lookup ->
                        fileForensics.identifyArea(lookup).also {
                            if (it == null) log(TAG, WARN) { "Failed to identify $lookup" }
                        }
                    }
            }
            .forEach { (type, infos) ->
                areaDataMap[type] = (areaDataMap[type] ?: emptySet()).plus(infos)
            }

        val supportedPublicAreas = setOf(
            DataArea.Type.PUBLIC_DATA,
            DataArea.Type.PUBLIC_MEDIA,
            DataArea.Type.SDCARD,
        )
        val useRoot = rootManager.canUseRootNow()

        currentAreas
            .filter { supportedPublicAreas.contains(it.type) }
            .mapNotNull { area ->
                try {
                    area to area.path.lookupFiles(gatewaySwitch)
                } catch (e: IOException) {
                    log(TAG, ERROR) { "Failed to lookup $area: ${e.asLog()}" }
                    null
                }
            }
            .map { (area, content) ->
                area.type to content
                    .filter { it.fileType == FileType.DIRECTORY }
                    .mapNotNull { lookup ->
                        fileForensics.identifyArea(lookup).also {
                            if (it == null) log(TAG, WARN) { "Failed to identify $lookup" }
                        }
                    }
                    .filter { areaInfo ->
                        val excluded = pathExclusions.any { it.match(areaInfo.file) }
                        val edgeCase = !useRoot && area.type == DataArea.Type.PUBLIC_DATA
                                && areaInfo.prefixFreeSegments.size >= 2
                                && areaInfo.prefixFreeSegments[1] == "cache"
                        if (excluded && edgeCase) {
                            log(TAG, WARN) { "Exclusion skipped to do default cache coverage: $areaInfo" }
                        } else if (excluded) {
                            log(TAG, WARN) { "Excluded during public area scan: $areaInfo" }
                        }
                        !excluded || edgeCase
                    }
            }
            .forEach { (type, infos) ->
                areaDataMap[type] = (areaDataMap[type] ?: emptySet()).plus(infos)
            }

        return areaDataMap
    }

    private suspend fun readAppDirs(
        searchPathsOfInterest: Map<AreaInfo, Collection<Installed.InstallId>>
    ): Map<Installed.InstallId, Collection<ExpendablesFilter.Match>> {
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_searching)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Percent(searchPathsOfInterest.size))

        val minCacheAgeMs = settings.minCacheAgeMs.value()
        val cutOffAge = Instant.now().minusMillis(minCacheAgeMs)
        log(TAG) { "minCacheAgeMs=$minCacheAgeMs -> Cut off after $cutOffAge" }

        val results = HashMap<Installed.InstallId, Collection<ExpendablesFilter.Match>>()

        val systemUser = userManager.systemUser()

        searchPathsOfInterest.entries.forEach spoi@{ target ->
            log(TAG, VERBOSE) { "Searching ${target.key.file} (${target.value})" }
            updateProgressSecondary(target.key.file.userReadablePath)

            val searchPath = target.key
            val possibleOwners = target.value

            val searchPathContents = try {
                searchPath.file
                    .walk(
                        gatewaySwitch,
                        options = APathGateway.WalkOptions(
                            pathDoesNotContain = setOf(
                                "/org.winehq.wine/files/prefix",
                                "/.wine/",
                            )
                        )
                    )
                    .toList()
            } catch (e: IOException) {
                log(TAG, WARN) { "Failed to read ${searchPath.file}: ${e.asLog()}" }
                emptyList()
            }

            val pathsOfInterest: List<Pair<APathLookup<APath>, Segments>> = searchPathContents
                .filter { minCacheAgeMs == 0L || it.modifiedAt < cutOffAge }
                .filter { it.segments.startsWith(searchPath.file.segments) }
                .map { it to it.removePrefix(searchPath.file, overlap = 1) }

            val ownersOfInterest = possibleOwners
                .filter { searchPath.userHandle == systemUser.handle || it.userHandle == searchPath.userHandle }

            pathsOfInterest.forEach { (path, segments) ->
                for (installId in ownersOfInterest) {
                    val match = enabledFilters.firstNotNullOfOrNull { filter ->
                        filter.match(
                            pkgId = installId.pkgId,
                            target = path,
                            areaType = searchPath.type,
                            segments = segments,
                        )

                    } ?: continue

                    log(TAG, INFO) { "${match.identifier.simpleName} matched ${searchPath.type}:$segments" }
                    results[installId] = (results[installId] ?: emptySet()).plus(match)
                    break
                }
            }

            increaseProgress()
        }

        return results
    }

    private suspend fun determineInaccessibleCaches(
        pkgs: Collection<Installed>,
    ): Collection<InaccessibleCache> {
        if (!settings.includeInaccessibleEnabled.value() || rootManager.canUseRootNow()) return emptyList()
        if (!settings.filterDefaultCachesPublicEnabled.value() || !settings.filterDefaultCachesPrivateEnabled.value()) {
            return emptyList()
        }
        val acsEnabled = settings.useAccessibilityService.value()
        val isSamsungRom = BuildWrap.MANUFACTOR == "Samsung"
        val currentUser = userManager.currentUser()

        return pkgs
            .filter { it.userHandle == currentUser.handle }
            .filter { pkg ->
                // On Samsung ROMs, we can't open the settings page for disabled apps
                !isSamsungRom || pkg.isEnabled
            }
            .filterIsInstance<NormalPkg>()
            .mapNotNull { inaccessibleCacheProvider.determineCache(it) }
            .filter { !it.isEmpty }
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Scanner")
    }
}
