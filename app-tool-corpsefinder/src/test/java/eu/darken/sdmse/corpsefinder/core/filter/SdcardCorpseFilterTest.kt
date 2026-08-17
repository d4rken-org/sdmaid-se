package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.flow.asFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.coroutine.runTest2
import java.io.File

class SdcardCorpseFilterTest : CorpseFilterTest() {

    @MockK lateinit var clutterRepo: ClutterRepo
    @MockK lateinit var pkgRepo: PkgRepo

    /**
     * SdcardCorpseFilter re-resolves case-insensitive spellings through the REAL filesystem
     * (`parentFile.listFiles()`), so the SDCARD area root has to be a directory we control.
     */
    @TempDir lateinit var tempDir: File

    override val sdcardRoot: LocalPath get() = LocalPath.build(tempDir)

    private val markers = mutableSetOf<Marker>()

    @BeforeEach
    override fun setup() {
        super.setup()
        markers.clear()
        coEvery { clutterRepo.getMarkerForLocation(any()) } returns emptySet()
        coEvery { pkgRepo.query(any(), any()) } returns emptySet()
        coEvery { fileForensics.identifyArea(any()) } returns null
    }

    private fun create() = SdcardCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        clutterRepo = clutterRepo,
        pkgRepo = pkgRepo,
    )

    /**
     * Mirrors [eu.darken.sdmse.common.clutter.manual.ManualMarker] for SDCARD: direct path match,
     * case-insensitive because public locations have a restricted charset.
     */
    private fun clutterMarker(
        pkg: String,
        segments: List<String>,
        isDirectMatch: Boolean = true,
        flags: Set<Marker.Flag> = emptySet(),
    ): Marker {
        val marker = mockk<Marker>().apply {
            every { areaType } returns DataArea.Type.SDCARD
            every { this@apply.segments } returns segments
            every { this@apply.flags } returns flags
            every { this@apply.isDirectMatch } returns isDirectMatch
            every { match(any<DataArea.Type>(), any<Segments>()) } answers {
                val otherType = firstArg<DataArea.Type>()
                val otherSegments = secondArg<Segments>()
                val hit = otherType == DataArea.Type.SDCARD && otherSegments.matches(segments, ignoreCase = true)
                if (hit) Marker.Match(setOf(Pkg.Id(name = pkg)), flags) else null
            }
        }
        markers.add(marker)
        coEvery { clutterRepo.getMarkerForLocation(DataArea.Type.SDCARD) } returns markers.toSet()
        return marker
    }

    private fun installed(pkg: String) {
        val installedPkg = mockk<Installed>().apply {
            every { id } returns Pkg.Id(name = pkg)
        }
        coEvery { pkgRepo.query(Pkg.Id(name = pkg), any()) } returns setOf(installedPkg)
    }

    /**
     * A path that only exists because a clutter marker points at it - it is NOT part of the area's
     * top level listing, the filter has to resolve it from the marker.
     */
    private fun markerTarget(
        area: DataArea,
        segments: List<String>,
        exists: Boolean = true,
        canRead: Boolean = true,
        fileType: FileType = FileType.DIRECTORY,
        children: List<String> = emptyList(),
        identifiedArea: DataArea = area,
    ): Candidate {
        val path = area.path.child(*segments.toTypedArray()) as LocalPath
        coEvery { gatewaySwitch.exists(path) } returns exists
        coEvery { gatewaySwitch.canRead(path) } returns canRead
        coEvery { fileForensics.identifyArea(path) } returns AreaInfo(
            file = path,
            prefix = identifiedArea.path,
            dataArea = identifiedArea,
            isBlackListLocation = false,
        )

        val lookup = pathLookup(path, fileType)
        coEvery { gatewaySwitch.lookup(path) } returns lookup
        val childLookups = children.map { pathLookup(path.child(it) as LocalPath, FileType.FILE) }
        if (fileType == FileType.DIRECTORY) {
            coEvery { gatewaySwitch.walk(path, any()) } returns childLookups.asFlow()
        }

        return Candidate(path = path, lookup = lookup, children = childLookups)
    }

    // ─────────────────────────── top level content ───────────────────────────

    @Test fun `top level item with an uninstalled owner is a corpse`() = runTest2 {
        val target = candidate(
            sdcard1,
            "OrphanApp",
            preset = Preset.StaleOwner("com.orphan.app"),
            children = listOf("data.db"),
        )

        create().scan().single().shouldMatch(target, SdcardCorpseFilter::class, RiskLevel.NORMAL)
    }

    @Test fun `top level item with an installed owner is not a corpse`() = runTest2 {
        candidate(sdcard1, "AliveApp", preset = Preset.InstalledOwner())

        create().scan() shouldBe emptyList()
    }

    @Test fun `top level items from both sdcard roots are aggregated`() = runTest2 {
        val first = candidate(sdcard1, "OrphanOne", preset = Preset.StaleOwner("com.orphan.one"))
        val second = candidate(sdcard2, "OrphanTwo", preset = Preset.StaleOwner("com.orphan.two"))

        create().scan().paths() shouldContainExactlyInAnyOrder listOf(first.path, second.path)
    }

    @Test fun `a top level file corpse has no content and is never walked`() = runTest2 {
        val target = candidate(
            sdcard1,
            "orphan.txt",
            preset = Preset.StaleOwner("com.orphan.app"),
            fileType = FileType.FILE,
        )

        val corpse = create().scan().single()

        corpse.shouldMatch(target, SdcardCorpseFilter::class)
        corpse.content shouldBe emptyList()
        coVerify(exactly = 0) { gatewaySwitch.walk(target.path, any()) }
    }

    // ─────────────────────────── clutter marker resolution ───────────────────────────

    @Test fun `a nested clutter marker with an uninstalled owner is a corpse`() = runTest2 {
        candidate(sdcard1, "Android", preset = Preset.InstalledOwner())
        clutterMarker(pkg = "com.orphan.app", segments = listOf("Android", "orphandata"))
        val target = markerTarget(sdcard1, listOf("Android", "orphandata"), children = listOf("blob"))

        val corpse = create().scan().single()

        corpse.shouldMatch(target, SdcardCorpseFilter::class, RiskLevel.NORMAL)
        corpse.ownerInfo.owners.map { it.pkgId.name } shouldBe listOf("com.orphan.app")
    }

    @Test fun `a nested clutter marker with an installed owner is not a corpse`() = runTest2 {
        candidate(sdcard1, "Android", preset = Preset.InstalledOwner())
        clutterMarker(pkg = "com.alive.app", segments = listOf("Android", "alivedata"))
        markerTarget(sdcard1, listOf("Android", "alivedata"))
        installed("com.alive.app")

        create().scan() shouldBe emptyList()
    }

    @Test fun `top level markers are not resolved again`() = runTest2 {
        candidate(sdcard1, "Android", preset = Preset.InstalledOwner())
        clutterMarker(pkg = "com.orphan.app", segments = listOf("Android"))
        val target = markerTarget(sdcard1, listOf("Android"))

        create().scan() shouldBe emptyList()

        // Single segment markers are dropped before any IO, the top level pass already covered them.
        coVerify(exactly = 0) { gatewaySwitch.exists(target.path) }
    }

    @Test fun `regex markers are not resolved`() = runTest2 {
        candidate(sdcard1, "Android", preset = Preset.InstalledOwner())
        clutterMarker(pkg = "com.orphan.app", segments = listOf("Android", "orphandata"), isDirectMatch = false)
        val target = markerTarget(sdcard1, listOf("Android", "orphandata"))

        create().scan() shouldBe emptyList()

        // Reverse matching a regex is impossible, so those markers never reach the filesystem.
        coVerify(exactly = 0) { gatewaySwitch.exists(target.path) }
    }

    @Test fun `markers whose top level parent is missing are not resolved`() = runTest2 {
        candidate(sdcard1, "Android", preset = Preset.InstalledOwner())
        clutterMarker(pkg = "com.orphan.app", segments = listOf("Missing", "orphandata"))
        val target = markerTarget(sdcard1, listOf("Missing", "orphandata"))

        create().scan() shouldBe emptyList()

        coVerify(exactly = 0) { gatewaySwitch.exists(target.path) }
    }

    @Test fun `markers pointing at a non existing path yield no corpse`() = runTest2 {
        candidate(sdcard1, "Android", preset = Preset.InstalledOwner())
        clutterMarker(pkg = "com.orphan.app", segments = listOf("Android", "orphandata"))
        markerTarget(sdcard1, listOf("Android", "orphandata"), exists = false)

        create().scan() shouldBe emptyList()
    }

    @Test fun `markers pointing at an unreadable path yield no corpse`() = runTest2 {
        candidate(sdcard1, "Android", preset = Preset.InstalledOwner())
        clutterMarker(pkg = "com.orphan.app", segments = listOf("Android", "orphandata"))
        markerTarget(sdcard1, listOf("Android", "orphandata"), canRead = false)

        create().scan() shouldBe emptyList()
    }

    @Test fun `marker casing is corrected to the on-disk spelling`() = runTest2 {
        // Real directory tree: the marker says "MiBand", the sdcard holds "miband".
        File(tempDir, "Games/miband").mkdirs()

        candidate(sdcard1, "Games", preset = Preset.InstalledOwner())
        clutterMarker(pkg = "com.mi.band", segments = listOf("Games", "MiBand"))
        // The marker spelling exists as far as the (case-insensitive) gateway is concerned...
        coEvery { gatewaySwitch.exists(sdcard1.path.child("Games", "MiBand")) } returns true
        // ...but the corpse is reported with the real on-disk spelling.
        val onDisk = markerTarget(sdcard1, listOf("Games", "miband"))

        create().scan().single().shouldMatch(onDisk, SdcardCorpseFilter::class)
    }

    // ─────────────────────────── ancestor / coverage passes ───────────────────────────

    @Test fun `a dead parent is dropped when it contains a living item`() = runTest2 {
        candidate(sdcard1, "Shared", preset = Preset.StaleOwner("com.orphan.app"))
        clutterMarker(pkg = "com.alive.app", segments = listOf("Shared", "alivedata"))
        markerTarget(sdcard1, listOf("Shared", "alivedata"))
        installed("com.alive.app")

        create().scan() shouldBe emptyList()
    }

    @Test fun `a dead nested item survives a living parent`() = runTest2 {
        candidate(sdcard1, "Shared", preset = Preset.InstalledOwner())
        clutterMarker(pkg = "com.orphan.app", segments = listOf("Shared", "orphandata"))
        val target = markerTarget(sdcard1, listOf("Shared", "orphandata"))

        create().scan().single().shouldMatch(target, SdcardCorpseFilter::class)
    }

    @Test fun `a dead nested item covered by a dead parent is dropped`() = runTest2 {
        val parent = candidate(sdcard1, "Shared", preset = Preset.StaleOwner("com.orphan.app"))
        clutterMarker(pkg = "com.orphan.nested", segments = listOf("Shared", "orphandata"))
        markerTarget(sdcard1, listOf("Shared", "orphandata"))

        create().scan().paths() shouldBe setOf(parent.path)
    }

    // ─────────────────────────── risk gating ───────────────────────────

    @Test fun `keepers are treated as alive when keepers are excluded`() = runTest2 {
        riskFlags(keeper = false)
        candidate(sdcard1, "KeeperDir", preset = Preset.Keeper())

        create().scan() shouldBe emptyList()
    }

    @Test fun `keepers are KEEPER corpses when keepers are included`() = runTest2 {
        riskFlags(keeper = true)
        val target = candidate(sdcard1, "KeeperDir", preset = Preset.Keeper())

        create().scan().single().shouldMatch(target, SdcardCorpseFilter::class, RiskLevel.KEEPER)
    }

    @Test fun `common items are treated as alive when common items are excluded`() = runTest2 {
        riskFlags(common = false)
        candidate(sdcard1, "CommonDir", preset = Preset.Common())

        create().scan() shouldBe emptyList()
    }

    @Test fun `common items are COMMON corpses when common items are included`() = runTest2 {
        riskFlags(common = true)
        val target = candidate(sdcard1, "CommonDir", preset = Preset.Common())

        create().scan().single().shouldMatch(target, SdcardCorpseFilter::class, RiskLevel.COMMON)
    }

    @Test fun `an excluded keeper does not block corpses below it`() = runTest2 {
        riskFlags(keeper = false)
        candidate(sdcard1, "KeeperDir", preset = Preset.Keeper())
        clutterMarker(pkg = "com.orphan.app", segments = listOf("KeeperDir", "orphandata"))
        markerTarget(sdcard1, listOf("KeeperDir", "orphandata"))

        // The keeper counts as alive, but it only blocks corpses ABOVE it, not below.
        create().scan().paths() shouldBe setOf(sdcard1.path.child("KeeperDir", "orphandata"))
    }

    // ─────────────────────────── write protection ───────────────────────────

    @Test fun `isWriteProtected is false when the path is writable`() = runTest2 {
        val target = candidate(sdcard1, "OrphanApp", preset = Preset.StaleOwner("com.orphan.app"))
        coEvery { gatewaySwitch.canWrite(target.path) } returns true

        create().scan().single().isWriteProtected shouldBe false
    }

    @Test fun `isWriteProtected is true when the path is not writable`() = runTest2 {
        val target = candidate(sdcard1, "OrphanApp", preset = Preset.StaleOwner("com.orphan.app"))
        coEvery { gatewaySwitch.canWrite(target.path) } returns false

        create().scan().single().isWriteProtected shouldBe true
    }

    @Test fun `an unidentified top level item is skipped and the scan continues`() = runTest2 {
        unidentifiedCandidate(sdcard1, "Unidentified")
        val target = candidate(sdcard1, "OrphanApp", preset = Preset.StaleOwner("com.orphan.app"))

        // Same contract as the template filters: an entry the CSI can't identify is logged and
        // skipped, it does not take the rest of the scan down with it.
        create().scan().single().shouldMatch(target, SdcardCorpseFilter::class)
    }

    // ─────────────────────────── marker cache lifecycle ───────────────────────────

    @Test fun `a second scan reflects a marker target that disappeared`() = runTest2 {
        candidate(sdcard1, "Android", preset = Preset.InstalledOwner())
        clutterMarker(pkg = "com.orphan.app", segments = listOf("Android", "orphandata"))
        val target = markerTarget(sdcard1, listOf("Android", "orphandata"))

        val filter = create()
        filter.scan().single().shouldMatch(target, SdcardCorpseFilter::class)

        coEvery { gatewaySwitch.exists(target.path) } returns false

        filter.scan() shouldBe emptyList()
    }

    @Test fun `a filter instance recovers from a failed scan`() = runTest2 {
        candidate(sdcard1, "Android", preset = Preset.InstalledOwner())
        clutterMarker(pkg = "com.orphan.app", segments = listOf("Android", "orphandata"))
        val target = markerTarget(sdcard1, listOf("Android", "orphandata"))
        // Blows up AFTER the marker cache was populated.
        coEvery { gatewaySwitch.lookup(target.path) } throws ReadException(path = target.path)

        val filter = create()
        shouldThrow<ReadException> { filter.scan() }

        // The cache is cleared in a `finally`, so the second scan re-checks existence instead of
        // reusing the entry from the failed run (which would trip the throwing lookup again).
        coEvery { gatewaySwitch.exists(target.path) } returns false

        filter.scan() shouldBe emptyList()
    }
}
