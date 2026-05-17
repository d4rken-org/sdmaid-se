package eu.darken.sdmse.systemcleaner.core

import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class SystemCleanerExclusionTest : BaseTest() {

    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun stopKeepAliveScope() {
        keepAliveScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun `exclude saves PathExclusions tagged SYSTEMCLEANER`() = runTest2 {
        val match = fakeMatch(name = "to-exclude", size = 100L)
        val fc = fakeFilterContent(identifier = "f", items = listOf(match))
        val savedSlot = slot<Set<Exclusion>>()
        val harness = SystemCleanerHarness(keepAliveScope)
        val saved = setOf(PathExclusion(path = match.path, tags = setOf(Exclusion.Tag.SYSTEMCLEANER)))
        val cleaner = harness.build(
            crawlerResults = listOf(fc),
            savedExclusions = saved,
            captureSavedExclusions = savedSlot,
        )
        cleaner.submit(SystemCleanerScanTask())

        cleaner.exclude(identifier = "f", exclusionTargets = setOf(match.path))

        savedSlot.captured.size shouldBe 1
        val excl = savedSlot.captured.single()
        excl.shouldBe(PathExclusion(path = match.path, tags = setOf(Exclusion.Tag.SYSTEMCLEANER)))
        // Tag set must contain exactly SYSTEMCLEANER, not GENERAL or any other.
        excl.tags shouldBe setOf(Exclusion.Tag.SYSTEMCLEANER)
    }

    @Test
    fun `exclude returns ExclusionUndo with exclusionIds from save result not from input set size`() = runTest2 {
        // Analog of CorpseFinder fix #5: the snackbar count comes from
        // `saved.map { it.id }.toSet()`, NOT the count of input paths. If `save()`
        // coalesces duplicates, the undo reflects what was actually persisted.
        val matchA = fakeMatch(name = "a", size = 100L)
        val matchB = fakeMatch(name = "b", size = 50L)
        val fc = fakeFilterContent(identifier = "f", items = listOf(matchA, matchB))
        val harness = SystemCleanerHarness(keepAliveScope)
        // Caller asked for 2 paths; storage layer coalesced to 1.
        val saved = setOf(PathExclusion(path = matchA.path, tags = setOf(Exclusion.Tag.SYSTEMCLEANER)))
        val cleaner = harness.build(
            crawlerResults = listOf(fc),
            savedExclusions = saved,
        )
        cleaner.submit(SystemCleanerScanTask())

        val undo = cleaner.exclude(identifier = "f", exclusionTargets = setOf(matchA.path, matchB.path))

        undo.exclusionIds.size shouldBe 1
        undo.exclusionIds shouldBe setOf(PathExclusion.createId(matchA.path))
    }

    @Test
    fun `exclude removes matching paths from internalData`() = runTest2 {
        val matchA = fakeMatch(name = "a", size = 100L)
        val matchB = fakeMatch(name = "b", size = 50L)
        val fc = fakeFilterContent(identifier = "f", items = listOf(matchA, matchB))
        val harness = SystemCleanerHarness(keepAliveScope)
        val saved = setOf(PathExclusion(path = matchA.path, tags = setOf(Exclusion.Tag.SYSTEMCLEANER)))
        val cleaner = harness.build(crawlerResults = listOf(fc), savedExclusions = saved)
        cleaner.submit(SystemCleanerScanTask())

        cleaner.exclude(identifier = "f", exclusionTargets = setOf(matchA.path))

        cleaner.dataFromState()!!.filterContents.single().items.map { it.path } shouldBe listOf(matchB.path)
    }

    @Test
    fun `exclude before any scan throws NullPointerException on internalData double-bang`() = runTest2 {
        // Documents current behavior: SystemCleaner.kt:250 `internalData.value!!` fires when
        // exclude() is called before any scan has populated data. Real callers don't trigger
        // this because the UI gates on data presence. The !! is brittle — a future fix would
        // either no-op or throw IllegalStateException with a clearer message.
        val harness = SystemCleanerHarness(keepAliveScope)
        val cleaner = harness.build()

        shouldThrow<NullPointerException> {
            cleaner.exclude(
                identifier = "any",
                exclusionTargets = setOf(fakeMatch(name = "x").path),
            )
        }
    }

    @Test
    fun `exclude with filterA also removes shared paths from filterB - cross-filter smear`() = runTest2 {
        // BUG-FIXME-3: exclude(identifier, paths) uses `identifier` only for logging
        // (SystemCleaner.kt:241). The internalData update at lines 251-254 maps over ALL
        // filterContents and applies the exclusion list to each. If two filters share a path,
        // both get pruned — even though only one was named by the caller.
        //
        // The fix would scope the prune to `it.identifier == identifier` (or accept that the
        // current behavior is correct because PathExclusion is persistence-level not
        // filter-scoped, and document this in the function contract). Flip this test once
        // the decision is made.
        val match = fakeMatch(name = "shared.dat", size = 60L)
        val fcA = fakeFilterContent(identifier = "fA", items = listOf(match))
        val fcB = fakeFilterContent(identifier = "fB", items = listOf(match))
        val harness = SystemCleanerHarness(keepAliveScope)
        val saved = setOf(PathExclusion(path = match.path, tags = setOf(Exclusion.Tag.SYSTEMCLEANER)))
        val cleaner = harness.build(crawlerResults = listOf(fcA, fcB), savedExclusions = saved)
        cleaner.submit(SystemCleanerScanTask())

        cleaner.exclude(identifier = "fA", exclusionTargets = setOf(match.path))

        // Both fA and fB have had `match` pruned even though only fA was named. Both
        // FilterContents become empty and get filtered out, leaving an empty filterContents.
        cleaner.dataFromState()!!.filterContents shouldBe emptyList()
    }

    @Test
    fun `undoExclude calls exclusionManager remove with handle exclusionIds`() = runTest2 {
        val match = fakeMatch(name = "x", size = 10L)
        val fc = fakeFilterContent(identifier = "f", items = listOf(match))
        val harness = SystemCleanerHarness(keepAliveScope)
        val saved = setOf(PathExclusion(path = match.path, tags = setOf(Exclusion.Tag.SYSTEMCLEANER)))
        val cleaner = harness.build(crawlerResults = listOf(fc), savedExclusions = saved)
        cleaner.submit(SystemCleanerScanTask())

        val undo = cleaner.exclude(identifier = "f", exclusionTargets = setOf(match.path))
        cleaner.undoExclude(undo)

        coVerify(exactly = 1) { harness.exclusionManager.remove(undo.exclusionIds) }
    }

    @Test
    fun `undoExclude restores previousData when state has not moved on`() = runTest2 {
        val matchA = fakeMatch(name = "a", size = 100L)
        val matchB = fakeMatch(name = "b", size = 50L)
        val fc = fakeFilterContent(identifier = "f", items = listOf(matchA, matchB))
        val harness = SystemCleanerHarness(keepAliveScope)
        val saved = setOf(PathExclusion(path = matchA.path, tags = setOf(Exclusion.Tag.SYSTEMCLEANER)))
        val cleaner = harness.build(crawlerResults = listOf(fc), savedExclusions = saved)
        cleaner.submit(SystemCleanerScanTask())

        val undo = cleaner.exclude(identifier = "f", exclusionTargets = setOf(matchA.path))
        // State now reflects the exclude: only matchB remains.
        cleaner.dataFromState()!!.filterContents.single().items.map { it.path } shouldBe listOf(matchB.path)

        cleaner.undoExclude(undo)

        // After undo, state restored to pre-exclude (both items present).
        cleaner.dataFromState()!!.filterContents.single().items.map { it.path } shouldContainExactlyInAnyOrder listOf(matchA.path, matchB.path)
    }

    @Test
    fun `undoExclude does not restore previousData when state has moved on`() = runTest2 {
        // If a new scan replaced internalData between exclude() and undoExclude(), the undo
        // must only remove exclusions and leave the new state untouched.
        val matchA = fakeMatch(name = "a", size = 100L)
        val matchB = fakeMatch(name = "b", size = 50L)
        val fcInitial = fakeFilterContent(identifier = "f", items = listOf(matchA, matchB))
        val harness = SystemCleanerHarness(keepAliveScope)
        val saved = setOf(PathExclusion(path = matchA.path, tags = setOf(Exclusion.Tag.SYSTEMCLEANER)))
        val cleaner = harness.build(crawlerResults = listOf(fcInitial), savedExclusions = saved)
        cleaner.submit(SystemCleanerScanTask())

        val undo = cleaner.exclude(identifier = "f", exclusionTargets = setOf(matchA.path))

        // Simulate state moving on: rerun scan with new results.
        val newMatch = fakeMatch(name = "new", size = 20L)
        val fcNew = fakeFilterContent(identifier = "g", items = listOf(newMatch))
        io.mockk.coEvery { harness.crawler.crawl(any()) } returns listOf(fcNew)
        cleaner.submit(SystemCleanerScanTask())

        cleaner.undoExclude(undo)

        // State unchanged from the new scan; restore did not happen.
        cleaner.dataFromState()!!.filterContents.map { it.identifier } shouldBe listOf("g")
        coVerify(exactly = 1) { harness.exclusionManager.remove(undo.exclusionIds) }
    }

    @Test
    fun `undoExclude skips exclusionManager remove when exclusionIds is empty`() = runTest2 {
        // If save() returns an empty set (everything coalesced or storage backed off),
        // exclusionIds is empty and undoExclude must not call remove() with an empty arg.
        val match = fakeMatch(name = "x", size = 10L)
        val fc = fakeFilterContent(identifier = "f", items = listOf(match))
        val harness = SystemCleanerHarness(keepAliveScope)
        // Empty saved set → empty exclusionIds.
        val cleaner = harness.build(crawlerResults = listOf(fc), savedExclusions = emptySet())
        cleaner.submit(SystemCleanerScanTask())

        val undo = cleaner.exclude(identifier = "f", exclusionTargets = setOf(match.path))
        undo.exclusionIds shouldBe emptySet()

        cleaner.undoExclude(undo)

        coVerify(exactly = 0) { harness.exclusionManager.remove(any<Set<String>>()) }
    }
}
