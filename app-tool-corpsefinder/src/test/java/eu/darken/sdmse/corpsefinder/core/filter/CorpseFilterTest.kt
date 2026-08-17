package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionHolder
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.UserExclusion
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import testhelpers.BaseTest
import testhelpers.mockDataStoreValue
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Shared harness for the [CorpseFilter] implementations.
 *
 * The mocked world is intentionally shallow: data areas exist, the gateway lists whatever a test
 * registered, and [FileForensics] answers with REAL [OwnerInfo] objects. The corpse-relevant
 * predicates ([OwnerInfo.isCorpse], [OwnerInfo.isOwned], [OwnerInfo.isKeeper], [OwnerInfo.isCommon])
 * are derived, so they are never mocked - mocking them would allow states the production CSI can
 * never produce.
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class CorpseFilterTest : BaseTest() {

    @MockK lateinit var areaManager: DataAreaManager
    @MockK lateinit var gatewaySwitch: GatewaySwitch
    @MockK lateinit var fileForensics: FileForensics
    @MockK lateinit var corpseFinderSettings: CorpseFinderSettings
    @MockK lateinit var exclusionManager: ExclusionManager

    lateinit var localGateway: LocalGateway

    /** SDCARD area root, overridable so tests can point it at a real (temp) directory. */
    open val sdcardRoot: LocalPath get() = LocalPath.build("/storage", "emulated", "0")

    /** SDK level the fake [hasApiLevel] reports unless a test calls [fakeSdk]. */
    open val defaultSdk: Int = 34

    val dataRoot1: LocalPath = LocalPath.build("/data")
    val dataRoot2: LocalPath = LocalPath.build("/mnt", "expand", "1a2b3c4d")

    val appSource1 = dataArea(DataArea.Type.APP_APP, dataRoot1.child("app"), primary = true)
    val appSource2 = dataArea(DataArea.Type.APP_APP, dataRoot2.child("app"))

    val appSourcePrivate1 = dataArea(DataArea.Type.APP_APP_PRIVATE, dataRoot1.child("app-private"), primary = true)
    val appSourcePrivate2 = dataArea(DataArea.Type.APP_APP_PRIVATE, dataRoot2.child("app-private"))

    val appLib1 = dataArea(DataArea.Type.APP_LIB, dataRoot1.child("app-lib"), primary = true)
    val appLib2 = dataArea(DataArea.Type.APP_LIB, dataRoot2.child("app-lib"))

    val appAsec1 = dataArea(DataArea.Type.APP_ASEC, dataRoot1.child("app-asec"), primary = true)
    val appAsec2 = dataArea(DataArea.Type.APP_ASEC, dataRoot2.child("app-asec"))

    val artProfile1 = dataArea(DataArea.Type.ART_PROFILE, dataRoot1.child("misc", "profiles", "cur", "0"), primary = true)
    val artProfile2 = dataArea(DataArea.Type.ART_PROFILE, dataRoot2.child("misc", "profiles", "cur", "0"))

    val dalvikProfile1 = dataArea(DataArea.Type.DALVIK_PROFILE, dataRoot1.child("dalvik-cache", "profiles"), primary = true)
    val dalvikProfile2 = dataArea(DataArea.Type.DALVIK_PROFILE, dataRoot2.child("dalvik-cache", "profiles"))

    val dalvikDex1 = dataArea(DataArea.Type.DALVIK_DEX, dataRoot1.child("dalvik-cache", "arm64"), primary = true)
    val dalvikDex2 = dataArea(DataArea.Type.DALVIK_DEX, dataRoot2.child("dalvik-cache", "arm64"))

    val privateData1 = dataArea(
        DataArea.Type.PRIVATE_DATA,
        LocalPath.build("/data_mirror", "data_de", "null", "0"),
        primary = true,
        userHandle = UserHandle2(0),
    )
    val privateData2 = dataArea(
        DataArea.Type.PRIVATE_DATA,
        LocalPath.build("/data_mirror", "data_ce", "null", "0"),
        userHandle = UserHandle2(0),
    )

    val sdcard1 by lazy { dataArea(DataArea.Type.SDCARD, sdcardRoot, primary = true, userHandle = UserHandle2(0)) }
    val sdcard2 by lazy {
        dataArea(DataArea.Type.SDCARD, LocalPath.build("/storage", "ABCD-EFGH"), userHandle = UserHandle2(0))
    }

    val publicData1 by lazy { childArea(sdcard1, DataArea.Type.PUBLIC_DATA, "Android", "data") }
    val publicData2 by lazy { childArea(sdcard2, DataArea.Type.PUBLIC_DATA, "Android", "data") }

    val publicObb1 by lazy { childArea(sdcard1, DataArea.Type.PUBLIC_OBB, "Android", "obb") }
    val publicObb2 by lazy { childArea(sdcard2, DataArea.Type.PUBLIC_OBB, "Android", "obb") }

    val publicMedia1 by lazy { childArea(sdcard1, DataArea.Type.PUBLIC_MEDIA, "Android", "media") }
    val publicMedia2 by lazy { childArea(sdcard2, DataArea.Type.PUBLIC_MEDIA, "Android", "media") }

    val dataAreas: Set<DataArea> by lazy {
        setOf(
            appSource1, appSource2,
            appSourcePrivate1, appSourcePrivate2,
            appLib1, appLib2,
            appAsec1, appAsec2,
            artProfile1, artProfile2,
            dalvikProfile1, dalvikProfile2,
            dalvikDex1, dalvikDex2,
            privateData1, privateData2,
            sdcard1, sdcard2,
            publicData1, publicData2,
            publicObb1, publicObb2,
            publicMedia1, publicMedia2,
        )
    }

    private fun dataArea(
        type: DataArea.Type,
        path: LocalPath,
        primary: Boolean = false,
        userHandle: UserHandle2 = UserHandle2(-1),
    ) = DataArea(
        path = path,
        type = type,
        flags = if (primary) setOf(DataArea.Flag.PRIMARY) else emptySet(),
        userHandle = userHandle,
    )

    private fun childArea(parent: DataArea, type: DataArea.Type, vararg segments: String) = parent.copy(
        type = type,
        path = parent.path.child(*segments),
    )

    /** The two roots we register for [type], in a stable order. */
    fun areasOf(type: DataArea.Type): List<DataArea> = dataAreas.filter { it.type == type }

    /**
     * Mirrors the CSIs: every area a corpse filter scans is a blacklist location, except SDCARD
     * where content has to be whitelisted through the clutter DB (see `SdcardCSI`).
     */
    val DataArea.Type.isBlackListLocation: Boolean
        get() = this != DataArea.Type.SDCARD

    private val areaContents = mutableMapOf<APath, MutableList<APath>>()
    private val installedExclusions = mutableListOf<Exclusion>()
    private var fakeSdkLevel: Int = 0

    /** The setting mocks [riskFlags] installed, so tests can verify how often a filter reads them. */
    lateinit var keeperSetting: DataStoreValue<Boolean>
    lateinit var commonSetting: DataStoreValue<Boolean>

    /** Called when a filter lists an area, for observing filter state mid-scan. */
    var onListFiles: ((APath) -> Unit)? = null

    /** Called when a filter consults forensics for a candidate, for observing filter state mid-scan. */
    var onFindOwners: ((APath) -> Unit)? = null

    @BeforeEach
    open fun setup() {
        MockKAnnotations.init(this)
        areaContents.clear()
        installedExclusions.clear()
        onListFiles = null
        onFindOwners = null

        localGateway = mockk()
        coEvery { gatewaySwitch.getGateway(APath.PathType.LOCAL) } returns localGateway
        coEvery { localGateway.hasRoot() } returns true
        coEvery { localGateway.hasAdb() } returns false

        every { areaManager.state } returns flowOf(DataAreaManager.State(areas = dataAreas))

        coEvery { gatewaySwitch.listFiles(any()) } answers {
            val path = arg<APath>(0)
            onListFiles?.invoke(path)
            areaContents[path]?.asFlow() ?: emptyFlow()
        }
        coEvery { gatewaySwitch.exists(any()) } returns false
        coEvery { gatewaySwitch.canRead(any()) } returns false
        coEvery { gatewaySwitch.canWrite(any()) } returns false

        riskFlags(keeper = false, common = false)
        refreshExclusions()

        // hasApiLevel(n) means "device SDK >= n", so a single monotonic answer keeps every call
        // consistent - stubbing individual levels would allow impossible SDK combinations.
        mockkStatic("eu.darken.sdmse.common.BuildWrapKt")
        every { hasApiLevel(any()) } answers { fakeSdkLevel >= firstArg<Int>() }
        fakeSdk(defaultSdk)
    }

    @AfterEach
    open fun teardown() {
        areaContents.clear()
        installedExclusions.clear()
        unmockkAll()
    }

    fun fakeSdk(level: Int) {
        fakeSdkLevel = level
    }

    fun hasRoot(hasRoot: Boolean) {
        coEvery { localGateway.hasRoot() } returns hasRoot
    }

    fun hasAdb(hasAdb: Boolean) {
        coEvery { localGateway.hasAdb() } returns hasAdb
    }

    fun riskFlags(keeper: Boolean = false, common: Boolean = false) {
        keeperSetting = mockDataStoreValue(keeper)
        commonSetting = mockDataStoreValue(common)
        every { corpseFinderSettings.includeRiskKeeper } returns keeperSetting
        every { corpseFinderSettings.includeRiskCommon } returns commonSetting
    }

    fun excludePath(path: APath) {
        installedExclusions.add(PathExclusion(path = path, tags = setOf(Exclusion.Tag.CORPSEFINDER)))
        refreshExclusions()
    }

    private fun refreshExclusions() {
        val holders: Collection<ExclusionHolder> = installedExclusions.map { UserExclusion(it) }
        every { exclusionManager.exclusions } returns flowOf(holders)
    }

    /**
     * Ownership fixtures. The resulting [OwnerInfo] predicates are computed, never stubbed.
     *
     * Not every shape is reachable in every area - the CSI that owns an area decides whether it can
     * report no owner at all, an unknown owner, or clutter flags. Pick the preset that the area's
     * CSI can actually produce.
     */
    sealed interface Preset {
        /** Nobody claims it, not even a stale owner -> corpse in a blacklist area. */
        data object BlacklistOrphan : Preset

        /** A known but uninstalled owner -> corpse. */
        data class StaleOwner(val pkg: String = "com.stale.owner") : Preset

        /** CSI knows someone owns it but can't name them -> not a corpse. */
        data object UnknownOwner : Preset

        /** Owner is currently installed -> not a corpse. */
        data class InstalledOwner(val pkg: String = "com.installed.owner") : Preset

        /** Corpse, but flagged as a keeper by the clutter DB. */
        data class Keeper(val pkg: String = "com.keeper.owner") : Preset

        /** Corpse, but flagged as commonly shared by the clutter DB. */
        data class Common(val pkg: String = "com.common.owner") : Preset
    }

    data class Candidate(
        val path: LocalPath,
        val lookup: LocalPathLookup,
        val children: List<LocalPathLookup>,
    )

    /**
     * Registers [name] as top level content of [area] and wires forensics for it.
     *
     * @param forensicArea the area the mocked [FileForensics] reports - set it to a different area
     * to simulate a CSI/filter area mismatch.
     */
    fun candidate(
        area: DataArea,
        name: String,
        preset: Preset = Preset.BlacklistOrphan,
        fileType: FileType = FileType.DIRECTORY,
        children: List<String> = emptyList(),
        forensicArea: DataArea = area,
        blacklistArea: Boolean = forensicArea.type.isBlackListLocation,
    ): Candidate {
        val path = area.path.child(name) as LocalPath
        registerContent(area, path)

        val lookup = pathLookup(path, fileType)
        coEvery { gatewaySwitch.lookup(path) } returns lookup

        val childLookups = children.map { pathLookup(path.child(it) as LocalPath, FileType.FILE) }
        if (fileType == FileType.DIRECTORY) {
            coEvery { gatewaySwitch.walk(path, any()) } returns childLookups.asFlow()
        }

        val owners = ownerInfo(path, preset, forensicArea, blacklistArea)
        coEvery { fileForensics.findOwners(path) } answers {
            onFindOwners?.invoke(path)
            owners
        }

        return Candidate(path = path, lookup = lookup, children = childLookups)
    }

    /** Registers content whose forensics come back `null` (CSI couldn't identify the area). */
    fun unidentifiedCandidate(area: DataArea, name: String): LocalPath {
        val path = area.path.child(name) as LocalPath
        registerContent(area, path)
        coEvery { fileForensics.findOwners(path) } answers {
            onFindOwners?.invoke(path)
            null
        }
        return path
    }

    private fun registerContent(area: DataArea, path: APath) {
        areaContents.getOrPut(area.path) { mutableListOf() }.add(path)
    }

    fun pathLookup(path: LocalPath, fileType: FileType) = LocalPathLookup(
        lookedUp = path,
        fileType = fileType,
        size = if (fileType == FileType.DIRECTORY) 512L else 1024L,
        modifiedAt = Instant.EPOCH,
        target = null,
    )

    fun ownerInfo(
        path: LocalPath,
        preset: Preset,
        area: DataArea,
        blacklistArea: Boolean = area.type.isBlackListLocation,
    ): OwnerInfo {
        val areaInfo = AreaInfo(
            file = path,
            prefix = area.path,
            dataArea = area,
            isBlackListLocation = blacklistArea,
        )
        fun owner(pkg: String, vararg flags: Marker.Flag) = Owner(
            pkgId = Pkg.Id(name = pkg),
            userHandle = area.userHandle,
            flags = flags.toSet(),
        )
        return when (preset) {
            is Preset.BlacklistOrphan -> OwnerInfo(
                areaInfo = areaInfo,
                owners = emptySet(),
                installedOwners = emptySet(),
                hasUnknownOwner = false,
            )

            is Preset.StaleOwner -> OwnerInfo(
                areaInfo = areaInfo,
                owners = setOf(owner(preset.pkg)),
                installedOwners = emptySet(),
                hasUnknownOwner = false,
            )

            is Preset.UnknownOwner -> OwnerInfo(
                areaInfo = areaInfo,
                owners = emptySet(),
                installedOwners = emptySet(),
                hasUnknownOwner = true,
            )

            is Preset.InstalledOwner -> OwnerInfo(
                areaInfo = areaInfo,
                owners = setOf(owner(preset.pkg)),
                installedOwners = setOf(owner(preset.pkg)),
                hasUnknownOwner = false,
            )

            is Preset.Keeper -> OwnerInfo(
                areaInfo = areaInfo,
                owners = setOf(owner(preset.pkg, Marker.Flag.KEEPER)),
                installedOwners = emptySet(),
                hasUnknownOwner = false,
            )

            is Preset.Common -> OwnerInfo(
                areaInfo = areaInfo,
                owners = setOf(owner(preset.pkg, Marker.Flag.COMMON)),
                installedOwners = emptySet(),
                hasUnknownOwner = false,
            )
        }
    }

    fun Collection<Corpse>.paths(): Set<APath> = map { it.identifier }.toSet()

    fun Corpse.shouldMatch(
        candidate: Candidate,
        filterType: KClass<out CorpseFilter>,
        riskLevel: RiskLevel = RiskLevel.NORMAL,
    ) {
        this.identifier shouldBe candidate.path
        this.filterType shouldBe filterType
        this.riskLevel shouldBe riskLevel
        this.lookup shouldBe candidate.lookup
        this.content.map { it.lookedUp }.toSet() shouldBe candidate.children.map { it.lookedUp }.toSet()
    }
}
