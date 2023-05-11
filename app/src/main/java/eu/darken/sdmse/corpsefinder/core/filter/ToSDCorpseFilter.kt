package eu.darken.sdmse.corpsefinder.core.filter

import dagger.Reusable
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.exists
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.files.listFiles
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.lookup
import eu.darken.sdmse.common.files.walk
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.exclusion.core.ExclusionManager
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class ToSDCorpseFilter @Inject constructor(
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val pkgRepo: PkgRepo,
    private val exclusionManager: ExclusionManager,
) : CorpseFilter(TAG, DEFAULT_PROGRESS) {


    override suspend fun doScan(): Collection<Corpse> {
        log(TAG) { "Scanning..." }

        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG) { "LocalGateway has no root, skipping public data on Android 13" }
            return emptySet()
        }

        val areas = areaManager.currentAreas()

        val results = mutableSetOf<Corpse>()
        results.addAll(digPublicData(areas))
        results.addAll(digPublicObb(areas))
        results.addAll(digDalvikProfile(areas))
        results.addAll(digDalvikCache(areas))
        results.addAll(digPrivateData(areas))
        results.addAll(digApkData(areas))
        results.addAll(digLibraryData(areas))
        return results
    }

    private suspend fun Collection<DataArea>.getCandidates(
        areaType: DataArea.Type,
        vararg segments: String
    ): Collection<APath> = this
        .asFlow()
        .filter { it.type == areaType }
        .map {
            updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_searching)
            it.path.child(*segments)
        }
        .filter { it.exists(gatewaySwitch) }
        .map {
            log(TAG) { "Searching: $it" }
            it.listFiles(gatewaySwitch)
        }
        .toList()
        .flatten()

    /**
     * Public data
     * Link2SD
     * /storage/emulated/0/Android/data/some.app > /data/sdext2/Link2SD/bind/data/some.app
     * /storage/emulated/0/Android/data/some.app > /storage/sdcard1/Link2SD/bind/data/some.app
     * Apps2SD:
     * /storage/emulated/0/Android/data/some.app > /data/sdext2/Android/data/some.app
     * /storage/emulated/0/Android/data/some.app > /storage/sdcard1/Apps2SD/Android/data/some.app
     */
    private suspend fun digPublicData(areas: Collection<DataArea>): Collection<Corpse> {
        log(TAG) { "Checking Link2SD & Apps2SD public data." }

        val candidates = mutableSetOf<APath>()

        candidates.addAll(areas.getCandidates(DataArea.Type.SDCARD, "Link2SD", "bind", "data"))
        candidates.addAll(areas.getCandidates(DataArea.Type.DATA_SDEXT2, "Link2SD", "bind", "data"))
        candidates.addAll(areas.getCandidates(DataArea.Type.SDCARD, "Apps2SD", "Android", "data"))
        candidates.addAll(areas.getCandidates(DataArea.Type.DATA_SDEXT2, "Android", "data"))

        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        return candidates
            .mapNotNull { fileForensics.identifyArea(it) }
            .map { areaInfo ->
                val dirPkg = areaInfo.file.name.toPkgId()
                val owners = setOf(Owner(dirPkg, areaInfo.userHandle))
                OwnerInfo(
                    areaInfo = areaInfo,
                    owners = owners,
                    installedOwners = owners.filter {
                        pkgRepo.isInstalled(it.pkgId, areaInfo.userHandle)
                    }.toSet(),
                    hasUnknownOwner = false,
                )
            }
            .filter { it.isCorpse }
            .map { ownerInfo ->
                val lookup = ownerInfo.item.lookup(gatewaySwitch)
                val content = if (lookup.isDirectory) ownerInfo.item.walk(gatewaySwitch).toSet() else emptyList()
                Corpse(
                    filterType = this::class,
                    ownerInfo = ownerInfo,
                    lookup = lookup,
                    content = content,
                    isWriteProtected = false,
                    riskLevel = when {
                        ownerInfo.isKeeper -> RiskLevel.KEEPER
                        ownerInfo.isCommon -> RiskLevel.COMMON
                        else -> RiskLevel.NORMAL
                    }
                ).also { log(TAG, INFO) { "Found Corpse: $it" } }
            }
    }

    /**
     * Obb data
     * Link2SD
     * /storage/emulated/0/Android/obb/some.app > /storage/sdcard1/Link2SD/bind/obb/some.app
     * /storage/emulated/0/Android/obb/some.app > /data/sdext2/Link2SD/bind/obb/some.app
     * Apps2SD
     * /storage/emulated/0/Android/obb/some.app > /storage/sdcard1/Apps2SD/Android/obb/some.app
     * /storage/emulated/0/Android/obb/some.app > /data/sdext2/Android/obb/some.app
     */
    private suspend fun digPublicObb(areas: Collection<DataArea>): Collection<Corpse> {
        log(TAG) { "Checking Link2SD & Apps2SD public obb." }

        val candidates = mutableSetOf<APath>()

        candidates.addAll(areas.getCandidates(DataArea.Type.SDCARD, "Link2SD", "bind", "obb"))
        candidates.addAll(areas.getCandidates(DataArea.Type.DATA_SDEXT2, "Link2SD", "bind", "obb"))
        candidates.addAll(areas.getCandidates(DataArea.Type.SDCARD, "Apps2SD", "Android", "obb"))
        candidates.addAll(areas.getCandidates(DataArea.Type.DATA_SDEXT2, "Android", "obb"))

        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        return candidates
            .mapNotNull { fileForensics.identifyArea(it) }
            .map { areaInfo ->
                val dirPkg = areaInfo.file.name.toPkgId()
                val owners = setOf(Owner(dirPkg, areaInfo.userHandle))
                OwnerInfo(
                    areaInfo = areaInfo,
                    owners = owners,
                    installedOwners = owners.filter {
                        pkgRepo.isInstalled(it.pkgId, areaInfo.userHandle)
                    }.toSet(),
                    hasUnknownOwner = false,
                )
            }
            .filter { it.isCorpse }
            .map { ownerInfo ->
                val lookup = ownerInfo.item.lookup(gatewaySwitch)
                val content = if (lookup.isDirectory) ownerInfo.item.walk(gatewaySwitch).toSet() else emptyList()
                Corpse(
                    filterType = this::class,
                    ownerInfo = ownerInfo,
                    lookup = lookup,
                    content = content,
                    isWriteProtected = false,
                    riskLevel = when {
                        ownerInfo.isKeeper -> RiskLevel.KEEPER
                        ownerInfo.isCommon -> RiskLevel.COMMON
                        else -> RiskLevel.NORMAL
                    }
                ).also { log(TAG, INFO) { "Found Corpse: $it" } }
            }
    }

    /**
     * Dalvik-Cache
     * /data/dalvik-cache/profiles/some.app is ignored by both Link2SD and Apps2SD.
     */
    private fun digDalvikProfile(areas: Collection<DataArea>): Collection<Corpse> {
        log(TAG) { "Checking Link2SD & Apps2SD dalvik-profile." }
        return emptySet()
    }

    /**
     * Dalvik-Cache
     * Link2SD
     * /data/dalvik-cache/arm/some.app-1.odex > /data/sdext2/dalvik-cache/arm/some.app-1.odex
     * /data/dalvik-cache/arm64/some.app-1.odex > /data/sdext2/dalvik-cache/arm64/some.app-1.odex
     * Apps2SD
     * /data/dalvik-cache/arm/some.app-1.odex > /data/sdext2/dalvik-cache/some.app-1.odex
     */
    private suspend fun digDalvikCache(areas: Collection<DataArea>): Collection<Corpse> {
        log(TAG) { "Checking Link2SD & Apps2SD dalvik-cache." }

        val candidates = mutableSetOf<APath>()

        // TODO Support x86?
        candidates.addAll(areas.getCandidates(DataArea.Type.DATA_SDEXT2, "dalvik-cache", "arm"))
        candidates.addAll(areas.getCandidates(DataArea.Type.DATA_SDEXT2, "dalvik-cache", "arm64"))
        candidates.addAll(areas.getCandidates(DataArea.Type.DATA_SDEXT2, "dalvik-cache"))

        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        return candidates
            .mapNotNull { fileForensics.identifyArea(it) }
            .mapNotNull { areaInfo ->
                val fileName: String = areaInfo.file.name
                val matcher = DALVIK_MATCHER.matchEntire(fileName) ?: return@mapNotNull null

                val dirPkg = matcher.groupValues[1].toPkgId()
                val owners = setOf(Owner(dirPkg, areaInfo.userHandle))
                OwnerInfo(
                    areaInfo = areaInfo,
                    owners = owners,
                    installedOwners = owners.filter { pkgRepo.isInstalled(it.pkgId, areaInfo.userHandle) }.toSet(),
                    hasUnknownOwner = false,
                )
            }
            .filter { it.isCorpse }
            .filter { ownerInfo ->
                val fileName = ownerInfo.item.name
                var nameToPath = fileName.replace("@classes.dex", "")
                nameToPath = nameToPath.replace("@classes.odex", "")
                nameToPath = nameToPath.replace("@classes.dex.art", "")
                nameToPath = nameToPath.replace("@classes.oat", "")
                val file = ownerInfo.item.child(*nameToPath.split("@").toTypedArray())

                val exists = file.exists(gatewaySwitch)
                if (exists) log(TAG) { "File exists: $file for $ownerInfo" }
                exists
            }
            .map { ownerInfo ->
                val lookup = ownerInfo.item.lookup(gatewaySwitch)
                val content = if (lookup.isDirectory) ownerInfo.item.walk(gatewaySwitch).toSet() else emptyList()
                Corpse(
                    filterType = this::class,
                    ownerInfo = ownerInfo,
                    lookup = lookup,
                    content = content,
                    isWriteProtected = false,
                    riskLevel = when {
                        ownerInfo.isKeeper -> RiskLevel.KEEPER
                        ownerInfo.isCommon -> RiskLevel.COMMON
                        else -> RiskLevel.NORMAL
                    }
                ).also { log(TAG, INFO) { "Found Corpse: $it" } }
            }
    }

    /**
     * Private data - Both
     * /data/data/app.package.dir > /data/sdext2/data/app.package.dir
     */
    private suspend fun digPrivateData(areas: Collection<DataArea>): Collection<Corpse> {
        log(TAG) { "Checking Link2SD & Apps2SD private data." }

        val candidates = areas.getCandidates(DataArea.Type.DATA_SDEXT2, "data")

        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        return candidates
            .mapNotNull { fileForensics.identifyArea(it) }
            .map { areaInfo ->
                val dirPkg = areaInfo.file.name.toPkgId()
                val owners = setOf(Owner(dirPkg, areaInfo.userHandle))
                OwnerInfo(
                    areaInfo = areaInfo,
                    owners = owners,
                    installedOwners = owners.filter { pkgRepo.isInstalled(it.pkgId, areaInfo.userHandle) }.toSet(),
                    hasUnknownOwner = false,
                )
            }
            .filter { it.isCorpse }
            .map { ownerInfo ->
                val lookup = ownerInfo.item.lookup(gatewaySwitch)
                val content = if (lookup.isDirectory) ownerInfo.item.walk(gatewaySwitch).toSet() else emptyList()
                Corpse(
                    filterType = this::class,
                    ownerInfo = ownerInfo,
                    lookup = lookup,
                    content = content,
                    isWriteProtected = false,
                    riskLevel = when {
                        ownerInfo.isKeeper -> RiskLevel.KEEPER
                        ownerInfo.isCommon -> RiskLevel.COMMON
                        else -> RiskLevel.NORMAL
                    }
                ).also { log(TAG, INFO) { "Found Corpse: $it" } }
            }
    }

    /**
     * APK data
     * Link2SD
     * /data/app/some.app-1 > /data/sdext2/some.app-1
     * /data/app/some.other.app-1.apk > /data/sdext2/some.other.app-1.apk
     * Apps2SD
     * /data/app/some.app-1 > /data/sdext2/apk/some.app-1
     * /data/app/some.other.app-1.apk > /data/sdext2/apk/some.other.app-1.apk
     */
    private suspend fun digApkData(areas: Collection<DataArea>): Collection<Corpse> {
        log(TAG) { "Checking Link2SD & Apps2SD apk data." }

        val candidates = mutableSetOf<APath>()
        candidates.addAll(areas.getCandidates(DataArea.Type.DATA_SDEXT2, "apk"))
        candidates.addAll(areas.getCandidates(DataArea.Type.DATA_SDEXT2))

        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        return candidates
            .mapNotNull { fileForensics.identifyArea(it) }
            .mapNotNull { areaInfo ->
                val name = areaInfo.file.name
                val candidateName: String = if (name.endsWith(".apk")) name.substring(0, name.length - 4) else name
                var matcher = APKDIR.matchEntire(candidateName)
                if (matcher == null) matcher = APPDIR_ANDROIDO.matchEntire(candidateName)
                if (matcher == null) return@mapNotNull null

                val dirPkg = matcher.groupValues[1].toPkgId()
                val owners = setOf(Owner(dirPkg, areaInfo.userHandle))
                OwnerInfo(
                    areaInfo = areaInfo,
                    owners = owners,
                    installedOwners = owners.filter { pkgRepo.isInstalled(it.pkgId, areaInfo.userHandle) }.toSet(),
                    hasUnknownOwner = false,
                )
            }
            .filter { it.isCorpse }
            .map { ownerInfo ->
                val lookup = ownerInfo.item.lookup(gatewaySwitch)
                val content = if (lookup.isDirectory) ownerInfo.item.walk(gatewaySwitch).toSet() else emptyList()
                Corpse(
                    filterType = this::class,
                    ownerInfo = ownerInfo,
                    lookup = lookup,
                    content = content,
                    isWriteProtected = false,
                    riskLevel = when {
                        ownerInfo.isKeeper -> RiskLevel.KEEPER
                        ownerInfo.isCommon -> RiskLevel.COMMON
                        else -> RiskLevel.NORMAL
                    }
                ).also { log(TAG, INFO) { "Found Corpse: $it" } }
            }
    }

    /**
     * App library data
     * Link2SD
     * /data/app-lib/some.app > /data/sdext2/app-lib/some.app-1 (Only on <5.0).
     * Apps2SD
     * /data/app/some.app/lib > /data/sdext2/app-lib/some.app-1
     * /data/app-lib/some.app > /data/sdext2/app-lib/some.app-1
     */
    private suspend fun digLibraryData(areas: Collection<DataArea>): Collection<Corpse> {
        log(TAG) { "Checking Link2SD & Apps2SD library data." }

        val candidates = areas.getCandidates(DataArea.Type.DATA_SDEXT2, "app-lib")

        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        return candidates
            .mapNotNull { fileForensics.identifyArea(it) }
            .mapNotNull { areaInfo ->
                val match = APPLIB_DIR.matchEntire(areaInfo.file.name) ?: return@mapNotNull null

                val dirPkg = match.groupValues[1].toPkgId()
                val owners = setOf(Owner(dirPkg, areaInfo.userHandle))
                OwnerInfo(
                    areaInfo = areaInfo,
                    owners = owners,
                    installedOwners = owners.filter { pkgRepo.isInstalled(it.pkgId, areaInfo.userHandle) }.toSet(),
                    hasUnknownOwner = false,
                )
            }
            .filter { it.isCorpse }
            .map { ownerInfo ->
                val lookup = ownerInfo.item.lookup(gatewaySwitch)
                val content = if (lookup.isDirectory) ownerInfo.item.walk(gatewaySwitch).toSet() else emptyList()
                Corpse(
                    filterType = this::class,
                    ownerInfo = ownerInfo,
                    lookup = lookup,
                    content = content,
                    isWriteProtected = false,
                    riskLevel = when {
                        ownerInfo.isKeeper -> RiskLevel.KEEPER
                        ownerInfo.isCommon -> RiskLevel.COMMON
                        else -> RiskLevel.NORMAL
                    }
                ).also { log(TAG, INFO) { "Found Corpse: $it" } }
            }
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: CorpseFinderSettings,
        private val filterProvider: Provider<ToSDCorpseFilter>
    ) : CorpseFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterAppToSdEnabled.value()
        override suspend fun create(): CorpseFilter = filterProvider.get()
    }

//    @InstallIn(SingletonComponent::class)
//    @Module
//    abstract class DIM {
//        @Binds @IntoSet abstract fun mod(mod: Factory): CorpseFilter.Factory
//    }

    companion object {
        val DEFAULT_PROGRESS = Progress.Data(
            primary = R.string.corpsefinder_filter_app2sd_label.toCaString(),
            secondary = eu.darken.sdmse.common.R.string.general_progress_loading.toCaString(),
            count = Progress.Count.Indeterminate()
        )
        val TAG: String = logTag("CorpseFinder", "Filter", "App2SD")
        private val DALVIK_MATCHER by lazy { Regex("^(?:.+?@)+?([\\w._\\-]+)-\\d+\\.(?:apk|jar|zip)@\\w+\\.(?:dex|odex|jar|art)$") }
        private val APKDIR by lazy { Regex("^([\\w.\\-]+)-[0-9]{1,4}$") }
        private val APPDIR_ANDROIDO by lazy { Regex("^([\\w.\\-]+)-[a-zA-Z0-9=_-]{24}$") }
        private val APPLIB_DIR by lazy { Regex("^([\\w.\\-]+)-[0-9]{1,4}$") }
    }
}