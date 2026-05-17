package eu.darken.sdmse.systemcleaner.core

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.PathException
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerProcessingTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Instant

class SystemCleanerProcessingTest : BaseTest() {

    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun stopKeepAliveScope() {
        keepAliveScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun `submit ProcessingTask without prior scan throws IllegalStateException Data is null`() = runTest2 {
        val cleaner = SystemCleanerHarness(keepAliveScope).build()

        val e = shouldThrow<IllegalStateException> {
            cleaner.submit(SystemCleanerProcessingTask())
        }
        e.message shouldBe "Data is null"
    }

    @Test
    fun `submit ProcessingTask with targetFilters null deletes all filters in snapshot`() = runTest2 {
        val matchA = fakeMatch(name = "a", size = 100L)
        val matchB = fakeMatch(name = "b", size = 50L)
        val fcA = fakeFilterContent(identifier = "fA", items = listOf(matchA))
        val fcB = fakeFilterContent(identifier = "fB", items = listOf(matchB))
        val filterA = FakeSystemCleanerFilter(identifier = "fA")
        val filterB = FakeSystemCleanerFilter(identifier = "fB")
        val cleaner = SystemCleanerHarness(keepAliveScope).build(
            crawlerResults = listOf(fcA, fcB),
            filtersForProcess = listOf(filterA, filterB),
        )
        cleaner.submit(SystemCleanerScanTask())

        val result = cleaner.submit(SystemCleanerProcessingTask())

        result.shouldBeInstanceOf<SystemCleanerProcessingTask.Success>()
        result.affectedSpace shouldBe 150L
        result.affectedPaths shouldContainExactlyInAnyOrder setOf(matchA.path, matchB.path)
        filterA.processInvocations shouldBe 1
        filterB.processInvocations shouldBe 1
        cleaner.dataFromState()!!.filterContents shouldBe emptyList()
    }

    @Test
    fun `submit ProcessingTask scopes to one filter when targetFilters is set`() = runTest2 {
        val matchA = fakeMatch(name = "a", size = 100L)
        val matchB = fakeMatch(name = "b", size = 50L)
        val fcA = fakeFilterContent(identifier = "fA", items = listOf(matchA))
        val fcB = fakeFilterContent(identifier = "fB", items = listOf(matchB))
        val filterA = FakeSystemCleanerFilter(identifier = "fA")
        val filterB = FakeSystemCleanerFilter(identifier = "fB")
        val cleaner = SystemCleanerHarness(keepAliveScope).build(
            crawlerResults = listOf(fcA, fcB),
            filtersForProcess = listOf(filterA, filterB),
        )
        cleaner.submit(SystemCleanerScanTask())

        val result = cleaner.submit(SystemCleanerProcessingTask(targetFilters = setOf("fA")))

        result.shouldBeInstanceOf<SystemCleanerProcessingTask.Success>()
        result.affectedPaths shouldBe setOf(matchA.path)
        filterA.processInvocations shouldBe 1
        filterB.processInvocations shouldBe 0
        // fB still present in internalData because it wasn't targeted.
        cleaner.dataFromState()!!.filterContents.map { it.identifier } shouldBe listOf("fB")
    }

    @Test
    fun `submit ProcessingTask with stale targetFilters throws NoSuchElementException`() = runTest2 {
        val match = fakeMatch(name = "real", size = 100L)
        val fc = fakeFilterContent(identifier = "real", items = listOf(match))
        val cleaner = SystemCleanerHarness(keepAliveScope).build(
            crawlerResults = listOf(fc),
            filtersForProcess = listOf(FakeSystemCleanerFilter(identifier = "real")),
        )
        cleaner.submit(SystemCleanerScanTask())

        // single { it.identifier == targetIdentifier } throws on no-match.
        shouldThrow<NoSuchElementException> {
            cleaner.submit(SystemCleanerProcessingTask(targetFilters = setOf("ghost")))
        }
    }

    @Test
    fun `submit ProcessingTask throws IllegalStateException when FilterSource lacks matching filter`() = runTest2 {
        // Snapshot has FC for "fA" but FilterSource.create(onlyEnabled=false) returns nothing
        // matching it. Engine throws IllegalStateException("No filter matches $id").
        val match = fakeMatch(name = "a", size = 100L)
        val fc = fakeFilterContent(identifier = "fA", items = listOf(match))
        val cleaner = SystemCleanerHarness(keepAliveScope).build(
            crawlerResults = listOf(fc),
            filtersForProcess = emptyList(),
        )
        cleaner.submit(SystemCleanerScanTask())

        val e = shouldThrow<IllegalStateException> {
            cleaner.submit(SystemCleanerProcessingTask(targetFilters = setOf("fA")))
        }
        e.message!! shouldBe "No filter matches fA"
    }

    @Test
    fun `submit ProcessingTask with targetContent filters per-filter items`() = runTest2 {
        val matchA = fakeMatch(name = "a", size = 100L)
        val matchB = fakeMatch(name = "b", size = 50L)
        val fc = fakeFilterContent(identifier = "f", items = listOf(matchA, matchB))
        val filter = FakeSystemCleanerFilter(identifier = "f")
        val cleaner = SystemCleanerHarness(keepAliveScope).build(
            crawlerResults = listOf(fc),
            filtersForProcess = listOf(filter),
        )
        cleaner.submit(SystemCleanerScanTask())

        val result = cleaner.submit(
            SystemCleanerProcessingTask(
                targetFilters = setOf("f"),
                targetContent = setOf(matchA.path),
            ),
        )

        result.shouldBeInstanceOf<SystemCleanerProcessingTask.Success>()
        // Only matchA processed; matchB stays.
        result.affectedPaths shouldBe setOf(matchA.path)
        filter.lastProcessInput shouldBe listOf(matchA)
        cleaner.dataFromState()!!.filterContents.single().items.map { it.path } shouldBe listOf(matchB.path)
    }

    @Test
    fun `submit ProcessingTask retains items whose process result is unsuccessful`() = runTest2 {
        val matchA = fakeMatch(name = "a", size = 100L)
        val matchB = fakeMatch(name = "b", size = 50L)
        val fc = fakeFilterContent(identifier = "f", items = listOf(matchA, matchB))
        // Filter returns success=false for matchB (error attached).
        val filter = FakeSystemCleanerFilter(
            identifier = "f",
            processResults = { matches ->
                matches.map { m ->
                    if (m === matchB) SystemCleanerFilter.Processed(match = m, error = RuntimeException("nope"))
                    else SystemCleanerFilter.Processed(match = m, error = null)
                }
            },
        )
        val cleaner = SystemCleanerHarness(keepAliveScope).build(
            crawlerResults = listOf(fc),
            filtersForProcess = listOf(filter),
        )
        cleaner.submit(SystemCleanerScanTask())

        val result = cleaner.submit(SystemCleanerProcessingTask(targetFilters = setOf("f")))

        result.shouldBeInstanceOf<SystemCleanerProcessingTask.Success>()
        // Only matchA accounted for; matchB still in state.
        result.affectedPaths shouldBe setOf(matchA.path)
        cleaner.dataFromState()!!.filterContents.single().items.map { it.path } shouldBe listOf(matchB.path)
    }

    @Test
    fun `submit ProcessingTask swallows PathException from filter and reports zero affected for that filter`() = runTest2 {
        val matchA = fakeMatch(name = "throws-here", size = 100L)
        val matchB = fakeMatch(name = "succeeds", size = 50L)
        val fcA = fakeFilterContent(identifier = "throwing", items = listOf(matchA))
        val fcB = fakeFilterContent(identifier = "ok", items = listOf(matchB))
        val throwingFilter = FakeThrowingFilter(
            identifier = "throwing",
            toThrow = PathException("simulated path error", path = matchA.path),
        )
        val okFilter = FakeSystemCleanerFilter(identifier = "ok")
        val cleaner = SystemCleanerHarness(keepAliveScope).build(
            crawlerResults = listOf(fcA, fcB),
            filtersForProcess = listOf(throwingFilter, okFilter),
        )
        cleaner.submit(SystemCleanerScanTask())

        val result = cleaner.submit(SystemCleanerProcessingTask())

        // Throwing filter is swallowed; matchA stays. Ok filter still runs; matchB processed.
        result.shouldBeInstanceOf<SystemCleanerProcessingTask.Success>()
        result.affectedPaths shouldBe setOf(matchB.path)
        result.affectedSpace shouldBe 50L
        throwingFilter.processInvocations shouldBe 1
        okFilter.processInvocations shouldBe 1
        // matchA's filter content stays; matchB's filter content drained.
        cleaner.dataFromState()!!.filterContents.map { it.identifier } shouldBe listOf("throwing")
    }

    @Test
    fun `submit ProcessingTask processed-by-ancestor removes child content and aggregates expectedGain`() = runTest2 {
        // SystemCleaner.kt:215-225: an item is considered processed when the processed match's
        // path isAncestorOf the item's path OR matches it. This means a parent path returned
        // by process() drains all descendants in the same FilterContent.
        val parentPath = LocalPath.build("storage", "emulated", "0", "parent")
        val childPath = LocalPath.build("storage", "emulated", "0", "parent", "child.dat")
        val parentLookup = LocalPathLookup(
            lookedUp = parentPath,
            fileType = FileType.DIRECTORY,
            size = 0L,
            modifiedAt = Instant.parse("2026-04-01T12:00:00Z"),
            target = null,
        )
        val childLookup = LocalPathLookup(
            lookedUp = childPath,
            fileType = FileType.FILE,
            size = 80L,
            modifiedAt = Instant.parse("2026-04-01T12:00:00Z"),
            target = null,
        )
        val parentMatch = SystemCleanerFilter.Match.Deletion(parentLookup)
        val childMatch = SystemCleanerFilter.Match.Deletion(childLookup)
        val fc = fakeFilterContent(identifier = "f", items = listOf(parentMatch, childMatch))
        // Filter processes only the parent. Child is drained by isAncestorOf.
        val filter = FakeSystemCleanerFilter(
            identifier = "f",
            processResults = { _ -> listOf(SystemCleanerFilter.Processed(match = parentMatch, error = null)) },
        )
        val cleaner = SystemCleanerHarness(keepAliveScope).build(
            crawlerResults = listOf(fc),
            filtersForProcess = listOf(filter),
        )
        cleaner.submit(SystemCleanerScanTask())

        val result = cleaner.submit(SystemCleanerProcessingTask())

        result.shouldBeInstanceOf<SystemCleanerProcessingTask.Success>()
        // Both parent and child paths drained; affected size sums their expectedGain.
        result.affectedPaths shouldContainExactlyInAnyOrder setOf(parentPath, childPath)
        result.affectedSpace shouldBe 80L // parent has size 0, child has size 80
        cleaner.dataFromState()!!.filterContents shouldBe emptyList()
    }

    @Test
    fun `submit ProcessingTask with targetFilters null and targetContent non-null applies content filter across all filters - cross-filter smear`() = runTest2 {
        // BUG-FIXME-2: ProcessingTask has no init { require } guard for targetContent !=
        // null && targetFilters == null. When called this way, targetFilters defaults to
        // every filter in the snapshot and `task.targetContent.contains(it.path)` is applied
        // against every filter's items — meaning a path that appears in two filters gets
        // pruned from both. CorpseFinder analog added the require() guard during its test
        // pass. Flip this test (and remove the BUG-FIXME comment) once the contract is added.
        val sharedPath = LocalPath.build("storage", "emulated", "0", "shared.dat")
        val sharedLookup = LocalPathLookup(
            lookedUp = sharedPath,
            fileType = FileType.FILE,
            size = 60L,
            modifiedAt = Instant.parse("2026-04-01T12:00:00Z"),
            target = null,
        )
        val sharedMatchA = SystemCleanerFilter.Match.Deletion(sharedLookup)
        val sharedMatchB = SystemCleanerFilter.Match.Deletion(sharedLookup)
        val unrelated = fakeMatch(name = "unrelated", size = 30L)
        val fcA = fakeFilterContent(identifier = "fA", items = listOf(sharedMatchA))
        val fcB = fakeFilterContent(identifier = "fB", items = listOf(sharedMatchB, unrelated))
        val filterA = FakeSystemCleanerFilter(identifier = "fA")
        val filterB = FakeSystemCleanerFilter(identifier = "fB")
        val cleaner = SystemCleanerHarness(keepAliveScope).build(
            crawlerResults = listOf(fcA, fcB),
            filtersForProcess = listOf(filterA, filterB),
        )
        cleaner.submit(SystemCleanerScanTask())

        val result = cleaner.submit(
            SystemCleanerProcessingTask(
                targetFilters = null,
                targetContent = setOf(sharedPath),
            ),
        )

        result.shouldBeInstanceOf<SystemCleanerProcessingTask.Success>()
        // Both filters' shared items got processed; filterB's `unrelated` survived because
        // the targetContent didn't include it.
        filterA.lastProcessInput shouldBe listOf(sharedMatchA)
        filterB.lastProcessInput shouldBe listOf(sharedMatchB)
        cleaner.dataFromState()!!.filterContents.map { it.identifier } shouldBe listOf("fB")
        cleaner.dataFromState()!!.filterContents.single().items.map { it.path } shouldBe listOf(unrelated.path)
    }
}
