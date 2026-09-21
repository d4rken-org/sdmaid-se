package eu.darken.sdmse.appcleaner.core.automation

import eu.darken.sdmse.automation.core.errors.AUTOMATION_FAILURE_LIMIT
import eu.darken.sdmse.automation.core.errors.InvalidSystemStateException
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.automation.core.errors.StepAbortException
import eu.darken.sdmse.automation.core.errors.UserCancelledAutomationException
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class ClearCacheLoopTest : BaseTest() {

    private val testHandle = UserHandle2(handleId = 0)
    private val succeeded = mutableListOf<InstallId>()
    private val errored = mutableListOf<Pair<InstallId, Exception>>()
    private val cleared = mutableListOf<InstallId>()
    private val resolveRequests = mutableListOf<InstallId>()

    private fun installId(pkgName: String) = InstallId(Pkg.Id(pkgName), testHandle)

    private fun installed(pkgName: String) = mockk<Installed>(relaxed = true).apply {
        every { label } returns pkgName.toCaString()
    }

    private val progressClient = object : Progress.Client {
        override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
            // The loop's progress updates are not what these tests are about.
        }
    }

    private suspend fun runLoop(
        vararg pkgNames: String,
        clearOne: suspend (InstallId) -> Unit,
    ): ClearCacheModule.ProcessedTask {
        val ids = pkgNames.map { installId(it) }
        val task = ClearCacheTask(
            targets = ids,
            returnToApp = false,
            onSuccess = { succeeded.add(it) },
            onError = { id, e -> errored.add(id to e) },
        )
        val targets = ids.associateWith { installed(it.pkgId.name) }
        return progressClient.clearCachesFor(
            task = task,
            resolveOne = { target ->
                resolveRequests.add(target)
                targets[target]
            },
            onTick = { },
            clearOne = { installed ->
                val id = targets.entries.first { (_, mock) -> mock === installed }.key
                cleared.add(id)
                clearOne(id)
            },
        )
    }

    @Test
    fun `a cleared cache is reported through onSuccess and listed as successful`() = runTest2 {
        val target = installId("pkg.a")

        val result = runLoop("pkg.a") { }

        succeeded shouldContainExactly listOf(target)
        result.successful shouldContainExactly listOf(target)
        result.failed.keys.shouldBeEmpty()
        result.cancelledByUser shouldBe false
    }

    @Test
    fun `a plan aborted as success is reported through onSuccess, not just returned`() = runTest2 {
        val target = installId("pkg.a")

        val result = runLoop("pkg.a") {
            throw PlanAbortException("No confirmation dialog", treatAsSuccess = true)
        }

        succeeded shouldContainExactly listOf(target)
        result.successful shouldContainExactly listOf(target)
        result.failed.keys.shouldBeEmpty()
    }

    @Test
    fun `an ordinary failure is reported through onError and listed as failed`() = runTest2 {
        val target = installId("pkg.a")

        val result = runLoop("pkg.a") { throw IllegalArgumentException("nope") }

        succeeded.shouldBeEmpty()
        errored.map { it.first } shouldContainExactly listOf(target)
        result.successful.shouldBeEmpty()
        result.failed.keys shouldContainExactly listOf(target)
    }

    @Test
    fun `a user cancel stops the loop and leaves later targets untouched`() = runTest2 {
        val first = installId("pkg.a")

        val result = runLoop("pkg.a", "pkg.b") { throw UserCancelledAutomationException() }

        cleared shouldContainExactly listOf(first)
        result.cancelledByUser shouldBe true
        result.successful.shouldBeEmpty()
        result.failed.keys.shouldBeEmpty()
    }

    @Test
    fun `an invalid system state escapes the loop`() = runTest2 {
        shouldThrow<InvalidSystemStateException> {
            runLoop("pkg.a", "pkg.b") { throw InvalidSystemStateException("no settings app") }
        }
        cleared shouldContainExactly listOf(installId("pkg.a"))
        succeeded.shouldBeEmpty()
    }

    @Test
    fun `an early exit skips the lookups for the remaining targets`() = runTest2 {
        val first = installId("pkg.a")

        val result = runLoop("pkg.a", "pkg.b", "pkg.c") { throw UserCancelledAutomationException() }

        result.cancelledByUser shouldBe true
        resolveRequests shouldContainExactly listOf(first)
    }

    @Test
    fun `a target missing from the package repo is failed without being cleared`() = runTest2 {
        val missing = installId("pkg.gone")
        val present = installId("pkg.a")
        val presentMock = installed(present.pkgId.name)
        val task = ClearCacheTask(
            targets = listOf(missing, present),
            returnToApp = false,
            onSuccess = { succeeded.add(it) },
            onError = { id, e -> errored.add(id to e) },
        )

        val result = progressClient.clearCachesFor(
            task = task,
            resolveOne = { target -> if (target == present) presentMock else null },
            onTick = { },
            clearOne = { cleared.add(present) },
        )

        cleared shouldContainExactly listOf(present)
        result.successful shouldContainExactly listOf(present)
        result.failed.keys shouldContainExactly listOf(missing)
        result.failed.getValue(missing).shouldBeInstanceOf<IllegalStateException>()
        result.failed.getValue(missing).message shouldBe "$missing is not in package repo"
    }

    @Test
    fun `the loop gives up once the failure limit is hit without a single success`() = runTest2 {
        val pkgNames = (1..AUTOMATION_FAILURE_LIMIT + 2).map { "pkg.$it" }
        val attempted = pkgNames.take(AUTOMATION_FAILURE_LIMIT).map { installId(it) }

        val result = runLoop(*pkgNames.toTypedArray()) { throw StepAbortException("Unreachable") }

        cleared shouldContainExactly attempted
        resolveRequests shouldContainExactly attempted
        result.failed.keys shouldContainExactly attempted
        result.successful.shouldBeEmpty()
        result.cancelledByUser shouldBe false
    }
}
