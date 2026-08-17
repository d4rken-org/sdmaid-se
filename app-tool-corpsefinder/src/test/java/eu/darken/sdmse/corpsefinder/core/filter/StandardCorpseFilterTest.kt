package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2
import kotlin.reflect.KClass

/**
 * The nine filters that share the "list area top level, run forensics, keep corpses" template.
 *
 * Everything that is identical between them lives here. Capability gates (root/adb), API cutoffs
 * and name exclusions differ per filter, so they get helpers here and explicit tests there.
 */
abstract class StandardCorpseFilterTest : CorpseFilterTest() {

    abstract val areaType: DataArea.Type
    abstract val filterClass: KClass<out CorpseFilter>

    abstract fun create(): CorpseFilter

    val area1: DataArea get() = areasOf(areaType)[0]
    val area2: DataArea get() = areasOf(areaType)[1]

    // ─────────────────────────── corpse identification ───────────────────────────

    @Test fun `blacklist orphan becomes a NORMAL corpse with walked content`() = runTest2 {
        val target = candidate(area1, "com.orphan.pkg", children = listOf("cache/file1", "file2"))

        val corpses = create().scan()

        corpses.single().shouldMatch(target, filterClass, RiskLevel.NORMAL)
    }

    @Test fun `whitelisted item with a stale owner becomes a corpse`() = runTest2 {
        val target = candidate(area1, "com.stale.pkg", preset = Preset.WhitelistStale())

        val corpses = create().scan()

        corpses.single().shouldMatch(target, filterClass, RiskLevel.NORMAL)
    }

    @Test fun `item with an installed owner is not a corpse`() = runTest2 {
        candidate(area1, "com.alive.pkg", preset = Preset.InstalledOwner())

        create().scan() shouldBe emptyList()
    }

    @Test fun `item with an unknown owner is not a corpse`() = runTest2 {
        candidate(area1, "com.mystery.pkg", preset = Preset.UnknownOwner)

        create().scan() shouldBe emptyList()
    }

    @Test fun `item reported for a different area is dropped`() = runTest2 {
        // CSI says this belongs to SDCARD while we are scanning `areaType`.
        candidate(area1, "com.wrongarea.pkg", forensicArea = sdcard1)

        create().scan() shouldBe emptyList()
    }

    @Test fun `unidentified item is skipped and the scan continues`() = runTest2 {
        unidentifiedCandidate(area1, "com.unidentified.pkg")
        val target = candidate(area1, "com.orphan.pkg")

        val corpses = create().scan()

        corpses.single().shouldMatch(target, filterClass)
    }

    // ─────────────────────────── risk gating ───────────────────────────

    @Test fun `keeper is dropped when keepers are excluded`() = runTest2 {
        riskFlags(keeper = false)
        candidate(area1, "com.keeper.pkg", preset = Preset.Keeper())

        create().scan() shouldBe emptyList()
    }

    @Test fun `keeper is a KEEPER corpse when keepers are included`() = runTest2 {
        riskFlags(keeper = true)
        val target = candidate(area1, "com.keeper.pkg", preset = Preset.Keeper())

        create().scan().single().shouldMatch(target, filterClass, RiskLevel.KEEPER)
    }

    @Test fun `common item is dropped when common items are excluded`() = runTest2 {
        riskFlags(common = false)
        candidate(area1, "com.common.pkg", preset = Preset.Common())

        create().scan() shouldBe emptyList()
    }

    @Test fun `common item is a COMMON corpse when common items are included`() = runTest2 {
        riskFlags(common = true)
        val target = candidate(area1, "com.common.pkg", preset = Preset.Common())

        create().scan().single().shouldMatch(target, filterClass, RiskLevel.COMMON)
    }

    // ─────────────────────────── exclusions ───────────────────────────

    @Test fun `path exclusion drops the candidate before forensics run`() = runTest2 {
        val excluded = candidate(area1, "com.excluded.pkg")
        val target = candidate(area1, "com.orphan.pkg")
        excludePath(excluded.path)

        val corpses = create().scan()

        corpses.single().shouldMatch(target, filterClass)
        // Exclusions are applied to the raw listing, forensics are the expensive part.
        coVerify(exactly = 0) { fileForensics.findOwners(excluded.path) }
    }

    // ─────────────────────────── shape of the result ───────────────────────────

    @Test fun `corpses from both roots of the area are aggregated`() = runTest2 {
        val first = candidate(area1, "com.orphan.one")
        val second = candidate(area2, "com.orphan.two")

        val corpses = create().scan()

        corpses.paths() shouldContainExactlyInAnyOrder listOf(first.path, second.path)
    }

    @Test fun `a file corpse has no content and is never walked`() = runTest2 {
        val target = candidate(area1, "com.orphan.pkg.file", fileType = FileType.FILE)

        val corpses = create().scan()

        corpses.single().shouldMatch(target, filterClass)
        corpses.single().content shouldBe emptyList()
        coVerify(exactly = 0) { gatewaySwitch.walk(target.path, any()) }
    }

    // ─────────────────────────── helpers for per-filter gates ───────────────────────────

    /** Asserts the filter skips the area entirely, without even listing it. */
    suspend fun assertSkipsScan() {
        candidate(area1, "com.orphan.pkg")

        create().scan() shouldBe emptySet()

        coVerify(exactly = 0) { gatewaySwitch.listFiles(any()) }
    }

    /** Asserts the filter does scan and finds the orphan we planted. */
    suspend fun assertScans() {
        val target = candidate(area1, "com.orphan.pkg")

        create().scan().single().shouldMatch(target, filterClass)
    }

    /** Asserts [name] is filtered out by name, before forensics are consulted. */
    suspend fun assertNameExcluded(name: String) {
        val excluded = candidate(area1, name)
        val target = candidate(area1, "com.orphan.pkg")

        val corpses = create().scan()

        corpses.single().shouldMatch(target, filterClass)
        coVerify(exactly = 0) { fileForensics.findOwners(excluded.path) }
    }
}
