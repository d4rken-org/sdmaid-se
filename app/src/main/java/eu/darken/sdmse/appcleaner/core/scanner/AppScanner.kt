package eu.darken.sdmse.appcleaner.core.scanner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.identifyArea
import eu.darken.sdmse.common.pkgs.*
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.currentExclusions
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.hasTags
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
    private val userManager: UserManager2,
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
    private var useRoot: Boolean = false

    suspend fun initialize() {
        log(TAG, VERBOSE) { "initialize()" }
        enabledFilters = filterFactories
            .filter { it.isEnabled() }
            .map { it.create() }
            .onEach { it.initialize() }
            .onEach { log(TAG, VERBOSE) { "Filter enabled: $it" } }
        log(TAG) { "${enabledFilters.size} filter are enabled" }
        useRoot = rootManager.useRoot()
        log(TAG) { "useRoot: $useRoot" }
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
        val includeOtherUsers = settings.includeOtherUsersEnabled.value()

        val pkgExclusions = exclusionManager.currentExclusions()
            .filter { it.hasTags(Exclusion.Tag.APPCLEANER) }
            .filterIsInstance<Exclusion.Package>()

        updateProgressSecondary(R.string.general_progress_loading_app_data)

        val currentUser = userManager.currentUser()
        val allUsers = userManager.allUsers()
        val allCurrentPkgs = pkgRepo.currentPkgs()
            .filter { includeOtherUsers || it.userHandle == currentUser.handle }
            .filter { includeSystemApps || !it.isSystemApp }
            .filter { pkgFilter.isEmpty() || pkgFilter.contains(it.id) }
            .filter { pkg ->
                val isExcluded = pkgExclusions.any { it.match(pkg.id) }
                if (isExcluded) log(TAG, INFO) { "Excluded package: ${pkg.id}" }
                !isExcluded
            }
            .filter { it.id.name != context.packageName }

        log(TAG) { "${allCurrentPkgs.size} apps to check :)" }

        val expendablesFromAppData: Map<Installed.InstallId, Collection<FilterMatch>> = buildSearchMap(allCurrentPkgs)
            .onEach { log(TAG) { "Searchmap contains ${it.value.size} pathes for ${it.key}." } }
            .let { readAppDirs(it) }

        val inaccessibleCaches = determineInaccessibleCaches(allCurrentPkgs)

        val appJunks = allCurrentPkgs.mapNotNull { pkg ->
            val expendables = expendablesFromAppData[pkg.installId]
            var inaccessible = inaccessibleCaches.firstOrNull { pkg.installId == it.identifier }
            if (expendables.isNullOrEmpty() && inaccessible == null) return@mapNotNull null

            val byFilterType: Map<KClass<out ExpendablesFilter>, Collection<APathLookup<*>>>? = expendables
                ?.groupBy { it.type }
                ?.mapValues { matches -> matches.value.map { it.file } }

            // For <API31 we can improve accuracy manually
            if (inaccessible != null && byFilterType != null && inaccessible.externalCacheBytes == null) {
                inaccessible = inaccessible.copy(
                    externalCacheBytes = byFilterType[DefaultCachesPublicFilter::class]?.sumOf { it.size }
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
        installedPkgs: Collection<Installed>,
    ): Map<AreaInfo, Collection<Installed.InstallId>> {
        updateProgressSecondary(R.string.general_progress_loading_data_areas)
        updateProgressCount(Progress.Count.Indeterminate())

        val dataAreaMap = createDataAreaMap()

        val searchPathMap = mutableMapOf<AreaInfo, Collection<Installed.InstallId>>()
        updateProgressPrimary(R.string.general_progress_generating_searchpaths)

        for (pkg in installedPkgs) {
            updateProgressSecondary(pkg.label?.get(context) ?: pkg.packageName)
            updateProgressCount(Progress.Count.Percent(0, installedPkgs.size))
            log(TAG) { "Generating search paths for ${pkg.installId}" }

            val interestingPaths = mutableSetOf<AreaInfo>()

            // The default private app data
            pkg.packageInfo.applicationInfo
                ?.dataDir
                ?.takeIf { it.isNotEmpty() && useRoot }
                ?.let { fileForensics.identifyArea(LocalPath.build(it)) }
                ?.let { interestingPaths.add(it) }

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
                        .any { it.match(candidate.type, candidate.prefixFreePath) != null }
                    if (indirectMatch) interestingPaths.add(candidate)
                }

            // Check all files on public (whitelist) storage (i.e. SDCARD) for potentially deeper nested clutter
            dataAreaMap[DataArea.Type.SDCARD]
                ?.forEach { topLevelArea ->
                    clutterMarkerForPkg
                        .filter { it.areaType == topLevelArea.type }
                        .filter { !it.hasFlags(Marker.Flag.CUSTODIAN) }
                        .filter { it.isDirectMatch } // Can't reverse lookup regex markers
                        .filter { it.segments.startsWith(topLevelArea.prefixFreePath, ignoreCase = true) }
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
            .forEach { owner ->
                val installIds = owner.installedOwners
                    .filter { !it.hasFlag(Marker.Flag.CUSTODIAN) }
                    .map { it.installId }
                searchPathMap[owner.areaInfo] = (searchPathMap[owner.areaInfo] ?: emptySet()).plus(installIds)
            }

        log(TAG) { "Search paths build (${searchPathMap.keys.size} interesting paths)." }

        return searchPathMap
    }

    /**
     * First we determine all areas that we check,
     * i.e. public/priv storage and some levels of the root of the sdcard
     */
    private suspend fun createDataAreaMap(): Map<DataArea.Type, Collection<AreaInfo>> {
        val currentAreas = areaManager.currentAreas()

        val pathExclusions = exclusionManager.currentExclusions()
            .filter { it.hasTags(Exclusion.Tag.APPCLEANER) }
            .filterIsInstance<Exclusion.Path>()

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
                                && areaInfo.prefixFreePath.size >= 2
                                && areaInfo.prefixFreePath[1] == "cache"
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

    data class FilterMatch(
        val file: APathLookup<*>,
        val type: KClass<out ExpendablesFilter>,
    )

    private suspend fun readAppDirs(
        searchPathsOfInterest: Map<AreaInfo, Collection<Installed.InstallId>>
    ): Map<Installed.InstallId, Collection<FilterMatch>> {
        updateProgressPrimary(R.string.general_progress_searching)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Percent(0, searchPathsOfInterest.size))

        val minCacheAgeMs = settings.minCacheAgeMs.value()

        val results = HashMap<Installed.InstallId, Collection<FilterMatch>>()

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
                log(TAG, WARN) { "Failed to read ${searchPath.file}: ${e.asLog()}" }
                emptyList()
            }

            val pathsOfInterest: List<Pair<APathLookup<APath>, Segments>> = searchPathContents
                .filter { minCacheAgeMs == 0L || it.modifiedAt >= Instant.now().minusMillis(minCacheAgeMs) }
                .filter { it.segments.startsWith(searchPath.file.segments) }
                .map { it to it.removePrefix(searchPath.file, overlap = 1) }

            val ownersOfInterest = possibleOwners
                .filter { searchPath.userHandle == systemUser.handle || it.userHandle == searchPath.userHandle }

            pathsOfInterest.forEach { (path, segments) ->
                for (userPkgId in ownersOfInterest) {
                    val type: KClass<out ExpendablesFilter> = enabledFilters
                        .firstOrNull { filter ->
                            filter.isExpendable(
                                pkgId = userPkgId.pkgId,
                                target = path,
                                areaType = searchPath.type,
                                segments = segments,
                            )
                        }
                        ?.javaClass?.kotlin
                        ?: continue

                    log(TAG, INFO) { "${type.simpleName} matched ${searchPath.type}:$segments" }
                    results[userPkgId] = (results[userPkgId] ?: emptySet()).plus(FilterMatch(path, type))
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
        if (!settings.includeInaccessibleEnabled.value() || rootManager.useRoot()) return emptyList()
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
                if (!isSamsungRom) return@filter true
                // TODO Is this our concern? Or should this be up to deletion, not the scan?
                (!acsEnabled || pkg.isEnabled).also {
                    if (!it) log(TAG, WARN) { "Skipping inaccessible cache for $pkg. Package disabled, ACS enabled." }
                }
            }
            .filterIsInstance<NormalPkg>()
            .mapNotNull { inaccessibleCacheProvider.determineCache(it) }
            .filter { !it.isEmpty }
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Scanner")
    }
}
