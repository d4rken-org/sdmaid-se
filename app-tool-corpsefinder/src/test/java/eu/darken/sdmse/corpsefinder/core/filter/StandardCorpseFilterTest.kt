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

    /**
     * The corpse shape the area's CSI actually produces for an abandoned item.
     *
     * [Preset.BlacklistOrphan] only fits CSIs that can return no owners at all. CSIs that always
     * fall back to a dirname owner (asec, public data/media, private data) can never emit that
     * state, so they declare [Preset.StaleOwner] instead.
     */
    abstract val defaultPreset: Preset

    abstract fun create(): CorpseFilter

    val area1: DataArea get() = areasOf(areaType)[0]
    val area2: DataArea get() = areasOf(areaType)[1]

    /** A blacklist area that is never the one under test, for the area mismatch scenario. */
    private val wrongArea: DataArea
        get() = if (areaType == DataArea.Type.PUBLIC_DATA) publicMedia1 else publicData1

    // ─────────────────────────── corpse identification ───────────────────────────

    @Test fun `an abandoned item becomes a NORMAL corpse with walked content`() = runTest2 {
        val target = candidate(area1, "com.orphan.pkg", preset = defaultPreset, children = listOf("file1", "file2"))

        val corpses = create().scan()

        corpses.single().shouldMatch(target, filterClass, RiskLevel.NORMAL)
    }

    @Test fun `item with an installed owner is not a corpse`() = runTest2 {
        candidate(area1, "com.alive.pkg", preset = Preset.InstalledOwner())

        create().scan() shouldBe emptyList()
    }

    @Test fun `item reported for a different area is dropped`() = runTest2 {
        // A perfectly good corpse, but the CSI attributed it to another area than the one we scan.
        candidate(area1, "com.wrongarea.pkg", preset = defaultPreset, forensicArea = wrongArea)

        create().scan() shouldBe emptyList()
    }

    @Test fun `unidentified item is skipped and the scan continues`() = runTest2 {
        unidentifiedCandidate(area1, "com.unidentified.pkg")
        val target = candidate(area1, "com.orphan.pkg", preset = defaultPreset)

        val corpses = create().scan()

        corpses.single().shouldMatch(target, filterClass)
    }

    // ─────────────────────────── exclusions ───────────────────────────

    @Test fun `path exclusion drops the candidate before forensics run`() = runTest2 {
        val excluded = candidate(area1, "com.excluded.pkg", preset = defaultPreset)
        val target = candidate(area1, "com.orphan.pkg", preset = defaultPreset)
        excludePath(excluded.path)

        val corpses = create().scan()

        corpses.single().shouldMatch(target, filterClass)
        // Exclusions are applied to the raw listing, forensics are the expensive part.
        coVerify(exactly = 0) { fileForensics.findOwners(excluded.path) }
    }

    // ─────────────────────────── shape of the result ───────────────────────────

    @Test fun `corpses from both roots of the area are aggregated`() = runTest2 {
        val first = candidate(area1, "com.orphan.one", preset = defaultPreset)
        val second = candidate(area2, "com.orphan.two", preset = defaultPreset)

        val corpses = create().scan()

        corpses.paths() shouldContainExactlyInAnyOrder listOf(first.path, second.path)
    }

    @Test fun `a file corpse has no content and is never walked`() = runTest2 {
        val target = candidate(area1, "com.orphan.pkg.file", preset = defaultPreset, fileType = FileType.FILE)

        val corpses = create().scan()

        corpses.single().shouldMatch(target, filterClass)
        corpses.single().content shouldBe emptyList()
        coVerify(exactly = 0) { gatewaySwitch.walk(target.path, any()) }
    }

    // ─────────────────────────── helpers for per-filter gates ───────────────────────────

    /** Asserts the filter skips the area entirely, without even listing it. */
    suspend fun assertSkipsScan() {
        candidate(area1, "com.orphan.pkg", preset = defaultPreset)

        create().scan() shouldBe emptySet()

        coVerify(exactly = 0) { gatewaySwitch.listFiles(any()) }
    }

    /** Asserts the filter does scan and finds the corpse we planted. */
    suspend fun assertScans() {
        val target = candidate(area1, "com.orphan.pkg", preset = defaultPreset)

        create().scan().single().shouldMatch(target, filterClass)
    }

    /**
     * Asserts the filter scans the area but keeps its findings to itself, which is what an untested
     * API level does. Distinct from [assertSkipsScan], where the area is never even listed.
     */
    suspend fun assertWithholdsScan() {
        candidate(area1, "com.orphan.pkg", preset = defaultPreset)

        create().scan() shouldBe emptySet()

        coVerify(atLeast = 1) { gatewaySwitch.listFiles(area1.path) }
    }

    /** Asserts [name] is filtered out by name, before forensics are consulted. */
    suspend fun assertNameExcluded(name: String) {
        val excluded = candidate(area1, name, preset = defaultPreset)
        val target = candidate(area1, "com.orphan.pkg", preset = defaultPreset)

        val corpses = create().scan()

        corpses.single().shouldMatch(target, filterClass)
        coVerify(exactly = 0) { fileForensics.findOwners(excluded.path) }
    }

    /**
     * For CSIs that can name an owner without it being installed. Only call this where the CSI can
     * emit owners it did not verify as installed (clutter matches, dirname fallbacks).
     */
    suspend fun assertStaleOwnerIsCorpse() {
        val target = candidate(area1, "com.stale.pkg", preset = Preset.StaleOwner())

        create().scan().single().shouldMatch(target, filterClass, RiskLevel.NORMAL)
    }

    /**
     * For CSIs that can report `hasKnownUnknownOwner`. Only call this where the CSI actually sets
     * that flag, otherwise the state is unreachable in production.
     */
    suspend fun assertUnknownOwnerIsNotCorpse() {
        candidate(area1, "com.mystery.pkg", preset = Preset.UnknownOwner)

        create().scan() shouldBe emptyList()
    }

    /**
     * For CSIs that pass clutter flags through to their owners. Asserts both directions of the
     * keeper risk setting.
     */
    suspend fun assertKeeperGating() {
        val target = candidate(area1, "com.keeper.pkg", preset = Preset.Keeper())

        riskFlags(keeper = false)
        create().scan() shouldBe emptyList()

        riskFlags(keeper = true)
        create().scan().single().shouldMatch(target, filterClass, RiskLevel.KEEPER)
    }

    /** See [assertKeeperGating], for the common flag. */
    suspend fun assertCommonGating() {
        val target = candidate(area1, "com.common.pkg", preset = Preset.Common())

        riskFlags(common = false)
        create().scan() shouldBe emptyList()

        riskFlags(common = true)
        create().scan().single().shouldMatch(target, filterClass, RiskLevel.COMMON)
    }
}
