package eu.darken.sdmse.corpsefinder.core.filter

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.SharedLibraryInfo
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class DalvikCorpseFilterTest : CorpseFilterTest() {

    @MockK lateinit var context: Context
    @MockK lateinit var packageManager: PackageManager

    @BeforeEach
    override fun setup() {
        super.setup()
        every { context.packageManager } returns packageManager
        // Default: the device knows no shared libraries, so checkForLibs() removes nothing.
        every { packageManager.getSharedLibraries(0) } returns mutableListOf()
    }

    private fun create() = DalvikCorpseFilter(
        context = context,
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    private fun sharedLibrary(name: String) = mockk<SharedLibraryInfo>().apply {
        every { this@apply.name } returns name
    }

    // ─────────────────────────── corpse identification ───────────────────────────

    @Test fun `profile and dex corpses are combined into one result`() = runTest2 {
        val profileCorpse = candidate(dalvikProfile1, "com.orphan.profile", fileType = FileType.FILE)
        val dexCorpse = candidate(dalvikDex1, "com.orphan.dex", fileType = FileType.FILE)

        val corpses = create().scan()

        corpses.paths() shouldContainExactlyInAnyOrder listOf(profileCorpse.path, dexCorpse.path)
        corpses.forEach { it.filterType shouldBe DalvikCorpseFilter::class }
    }

    @Test fun `profile corpses from both roots are aggregated`() = runTest2 {
        val first = candidate(dalvikProfile1, "com.orphan.one", fileType = FileType.FILE)
        val second = candidate(dalvikProfile2, "com.orphan.two", fileType = FileType.FILE)

        create().scan().paths() shouldContainExactlyInAnyOrder listOf(first.path, second.path)
    }

    @Test fun `dex corpses from both roots are aggregated`() = runTest2 {
        val first = candidate(dalvikDex1, "com.orphan.one", fileType = FileType.FILE)
        val second = candidate(dalvikDex2, "com.orphan.two", fileType = FileType.FILE)

        create().scan().paths() shouldContainExactlyInAnyOrder listOf(first.path, second.path)
    }

    @Test fun `a dalvik directory corpse carries its walked content`() = runTest2 {
        val target = candidate(dalvikDex1, "com.orphan.dir", children = listOf("classes.dex"))

        create().scan().single().shouldMatch(target, DalvikCorpseFilter::class)
    }

    @Test fun `items with an installed owner are not corpses`() = runTest2 {
        candidate(dalvikProfile1, "com.alive.profile", preset = Preset.InstalledOwner(), fileType = FileType.FILE)
        candidate(dalvikDex1, "com.alive.dex", preset = Preset.InstalledOwner(), fileType = FileType.FILE)

        create().scan() shouldBe emptyList()
    }

    @Test fun `items reported for a different area are dropped`() = runTest2 {
        // The dex pass only accepts DALVIK_DEX and the profile pass only DALVIK_PROFILE, so
        // swapping the areas must drop both.
        candidate(dalvikProfile1, "com.wrongarea.profile", fileType = FileType.FILE, forensicArea = dalvikDex1)
        candidate(dalvikDex1, "com.wrongarea.dex", fileType = FileType.FILE, forensicArea = dalvikProfile1)

        create().scan() shouldBe emptyList()
    }

    @Test fun `unidentified items are skipped and the scan continues`() = runTest2 {
        unidentifiedCandidate(dalvikProfile1, "com.unidentified.profile")
        unidentifiedCandidate(dalvikDex1, "com.unidentified.dex")
        val profileCorpse = candidate(dalvikProfile1, "com.orphan.profile", fileType = FileType.FILE)
        val dexCorpse = candidate(dalvikDex1, "com.orphan.dex", fileType = FileType.FILE)

        create().scan().paths() shouldContainExactlyInAnyOrder listOf(profileCorpse.path, dexCorpse.path)
    }

    // ─────────────────────────── risk gating ───────────────────────────

    @Test fun `keepers are dropped unless included`() = runTest2 {
        riskFlags(keeper = false)
        candidate(dalvikProfile1, "com.keeper.profile", preset = Preset.Keeper(), fileType = FileType.FILE)
        candidate(dalvikDex1, "com.keeper.dex", preset = Preset.Keeper(), fileType = FileType.FILE)

        create().scan() shouldBe emptyList()
    }

    @Test fun `keepers are KEEPER corpses when included`() = runTest2 {
        riskFlags(keeper = true)
        val profileCorpse = candidate(dalvikProfile1, "com.keeper.profile", preset = Preset.Keeper(), fileType = FileType.FILE)
        val dexCorpse = candidate(dalvikDex1, "com.keeper.dex", preset = Preset.Keeper(), fileType = FileType.FILE)

        val corpses = create().scan()

        corpses.paths() shouldContainExactlyInAnyOrder listOf(profileCorpse.path, dexCorpse.path)
        corpses.forEach { it.riskLevel shouldBe RiskLevel.KEEPER }
    }

    @Test fun `common items are dropped unless included`() = runTest2 {
        riskFlags(common = false)
        candidate(dalvikProfile1, "com.common.profile", preset = Preset.Common(), fileType = FileType.FILE)
        candidate(dalvikDex1, "com.common.dex", preset = Preset.Common(), fileType = FileType.FILE)

        create().scan() shouldBe emptyList()
    }

    @Test fun `common items are COMMON corpses when included`() = runTest2 {
        riskFlags(common = true)
        val profileCorpse = candidate(dalvikProfile1, "com.common.profile", preset = Preset.Common(), fileType = FileType.FILE)
        val dexCorpse = candidate(dalvikDex1, "com.common.dex", preset = Preset.Common(), fileType = FileType.FILE)

        val corpses = create().scan()

        corpses.paths() shouldContainExactlyInAnyOrder listOf(profileCorpse.path, dexCorpse.path)
        corpses.forEach { it.riskLevel shouldBe RiskLevel.COMMON }
    }

    // ─────────────────────────── exclusions ───────────────────────────

    @Test fun `path exclusions drop candidates before forensics run`() = runTest2 {
        val excludedProfile = candidate(dalvikProfile1, "com.excluded.profile", fileType = FileType.FILE)
        val excludedDex = candidate(dalvikDex1, "com.excluded.dex", fileType = FileType.FILE)
        val target = candidate(dalvikDex1, "com.orphan.dex", fileType = FileType.FILE)
        excludePath(excludedProfile.path)
        excludePath(excludedDex.path)

        create().scan().single().shouldMatch(target, DalvikCorpseFilter::class)

        coVerify(exactly = 0) { fileForensics.findOwners(excludedProfile.path) }
        coVerify(exactly = 0) { fileForensics.findOwners(excludedDex.path) }
    }

    // ─────────────────────────── capability and API gates ───────────────────────────

    @Test fun `without root nothing is scanned`() = runTest2 {
        hasRoot(false)
        hasAdb(true)
        candidate(dalvikDex1, "com.orphan.dex", fileType = FileType.FILE)

        create().scan() shouldBe emptySet()

        coVerify(exactly = 0) { gatewaySwitch.listFiles(any()) }
    }

    @Test fun `scans on API 36`() = runTest2 {
        fakeSdk(36)
        val target = candidate(dalvikDex1, "com.orphan.dex", fileType = FileType.FILE)

        create().scan().single().shouldMatch(target, DalvikCorpseFilter::class)
    }

    @Test fun `scans but withholds on API 37`() = runTest2 {
        fakeSdk(37)
        candidate(dalvikDex1, "com.orphan.dex", fileType = FileType.FILE)

        create().scan() shouldBe emptySet()

        // Unlike the no-root case above, the area is listed: only the findings are held back
        coVerify(atLeast = 1) { gatewaySwitch.listFiles(dalvikDex1.path) }
    }

    // ─────────────────────────── shared library filtering ───────────────────────────

    @Test fun `corpses owned by a shared library are ignored`() = runTest2 {
        // Owners are needed for the library match, so these use the stale-owner shape.
        val libOwned = candidate(
            dalvikDex1,
            "com.example.sharedlib",
            preset = Preset.StaleOwner("com.example.sharedlib"),
            fileType = FileType.FILE,
        )
        val unrelated = candidate(
            dalvikDex1,
            "com.example.app",
            preset = Preset.StaleOwner("com.example.app"),
            fileType = FileType.FILE,
        )
        every { packageManager.getSharedLibraries(0) } returns mutableListOf(sharedLibrary("com.example.sharedlib"))

        val corpses = create().scan()

        corpses.paths() shouldContainExactlyInAnyOrder listOf(unrelated.path)
        corpses.paths().contains(libOwned.path) shouldBe false
    }

    @Test fun `corpses are kept when no shared library matches`() = runTest2 {
        val profileCorpse = candidate(
            dalvikProfile1,
            "com.example.app",
            preset = Preset.StaleOwner("com.example.app"),
            fileType = FileType.FILE,
        )
        every { packageManager.getSharedLibraries(0) } returns mutableListOf(sharedLibrary("com.other.library"))

        create().scan().single().shouldMatch(profileCorpse, DalvikCorpseFilter::class)
    }
}
