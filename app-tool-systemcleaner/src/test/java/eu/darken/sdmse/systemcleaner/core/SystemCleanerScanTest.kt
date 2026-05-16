package eu.darken.sdmse.systemcleaner.core

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerSchedulerTask
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class SystemCleanerScanTest : BaseTest() {

    // SystemCleaner.submit() wraps work in keepResourceHoldersAlive(...), which calls
    // addChild(sharedResource) + sharedResource.get(). Wire each dependency to a real
    // SharedResource.createKeepAlive backed by a long-lived scope.
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun stopKeepAliveScope() {
        keepAliveScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun `submit ScanTask with empty filter set yields empty Success`() = runTest2 {
        val cleaner = SystemCleanerHarness(keepAliveScope).build(filtersForScan = emptyList())

        val result = cleaner.submit(SystemCleanerScanTask())

        result.shouldBeInstanceOf<SystemCleanerScanTask.Success>()
        cleaner.dataFromState()!!.filterContents shouldBe emptyList()
    }

    @Test
    fun `submit ScanTask with crawler results populates internalData`() = runTest2 {
        val f1 = fakeFilterContent(identifier = "f1", items = listOf(fakeMatch(name = "a", size = 100L)))
        val f2 = fakeFilterContent(identifier = "f2", items = listOf(fakeMatch(name = "b", size = 200L)))
        val cleaner = SystemCleanerHarness(keepAliveScope).build(crawlerResults = listOf(f1, f2))

        cleaner.submit(SystemCleanerScanTask())

        val data = cleaner.dataFromState()!!
        data.filterContents.map { it.identifier } shouldContainExactlyInAnyOrder listOf("f1", "f2")
    }

    @Test
    fun `submit ScanTask reports itemCount and recoverableSpace from crawler results`() = runTest2 {
        val f1 = fakeFilterContent(
            identifier = "f1",
            items = listOf(fakeMatch(name = "a", size = 100L), fakeMatch(name = "b", size = 50L)),
        )
        val f2 = fakeFilterContent(
            identifier = "f2",
            items = listOf(fakeMatch(name = "c", size = 25L)),
        )
        val cleaner = SystemCleanerHarness(keepAliveScope).build(crawlerResults = listOf(f1, f2))

        val result = cleaner.submit(SystemCleanerScanTask())

        result.shouldBeInstanceOf<SystemCleanerScanTask.Success>()
        cleaner.dataFromState()!!.totalCount shouldBe 3
        cleaner.dataFromState()!!.totalSize shouldBe 175L
    }

    @Test
    fun `submit ScanTask calls FilterSource with onlyEnabled true`() = runTest2 {
        val onlyEnabledSlot = slot<Boolean>()
        val cleaner = SystemCleanerHarness(keepAliveScope).build(
            crawlerResults = emptyList(),
            captureOnlyEnabled = onlyEnabledSlot,
        )

        cleaner.submit(SystemCleanerScanTask())

        onlyEnabledSlot.captured shouldBe true
    }

    @Test
    fun `submit ScanTask with non-default pkgIdFilter and isWatcherTask behaves identically to defaults`() = runTest2 {
        // BUG-FIXME-1: ScanTask.pkgIdFilter and isWatcherTask are accepted but ignored by
        // performScan (mirror of CorpseFinder's pre-fix dead-code state). Flip this test if
        // the engine ever starts consuming those fields.
        val match = fakeMatch(name = "m", size = 100L)
        val fc = fakeFilterContent(identifier = "f1", items = listOf(match))
        val cleaner = SystemCleanerHarness(keepAliveScope).build(crawlerResults = listOf(fc))

        val task = SystemCleanerScanTask(
            pkgIdFilter = setOf(Pkg.Id(name = "com.no.effect")),
            isWatcherTask = true,
        )
        val result = cleaner.submit(task)

        result.shouldBeInstanceOf<SystemCleanerScanTask.Success>()
        cleaner.dataFromState()!!.filterContents.map { it.identifier } shouldBe listOf("f1")
    }

    @Test
    fun `submit SchedulerTask chains scan and processing and returns SchedulerTask Success`() = runTest2 {
        val match = fakeMatch(name = "scheduled", size = 100L)
        val fc = fakeFilterContent(identifier = "f1", items = listOf(match))
        val processingFilter = FakeSystemCleanerFilter(identifier = "f1")
        val cleaner = SystemCleanerHarness(keepAliveScope).build(
            crawlerResults = listOf(fc),
            filtersForProcess = listOf(processingFilter),
        )

        val result = cleaner.submit(SystemCleanerSchedulerTask(schedulerId = "sched-1"))

        result.shouldBeInstanceOf<SystemCleanerSchedulerTask.Success>()
        result.affectedSpace shouldBe 100L
        result.affectedPaths shouldBe setOf(match.path)
        processingFilter.processInvocations shouldBe 1
    }

    @Test
    fun `submit OneClickTask chains scan and processing and returns OneClickTask Success`() = runTest2 {
        val match = fakeMatch(name = "oneclick", size = 250L)
        val fc = fakeFilterContent(identifier = "f1", items = listOf(match))
        val processingFilter = FakeSystemCleanerFilter(identifier = "f1")
        val cleaner = SystemCleanerHarness(keepAliveScope).build(
            crawlerResults = listOf(fc),
            filtersForProcess = listOf(processingFilter),
        )

        val result = cleaner.submit(SystemCleanerOneClickTask())

        result.shouldBeInstanceOf<SystemCleanerOneClickTask.Success>()
        result.affectedSpace shouldBe 250L
        result.affectedPaths shouldBe setOf(match.path)
    }
}
