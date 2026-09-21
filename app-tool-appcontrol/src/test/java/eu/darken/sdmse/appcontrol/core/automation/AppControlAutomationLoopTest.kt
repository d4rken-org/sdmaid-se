package eu.darken.sdmse.appcontrol.core.automation

import eu.darken.sdmse.automation.core.errors.AUTOMATION_FAILURE_LIMIT
import eu.darken.sdmse.automation.core.errors.AutomationOverlayException
import eu.darken.sdmse.automation.core.errors.AutomationTimeoutException
import eu.darken.sdmse.automation.core.errors.InvalidSystemStateException
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

class AppControlAutomationLoopTest : BaseTest() {

    private val testHandle = UserHandle2(handleId = 0)
    private val processed = mutableListOf<InstallId>()
    private val resolveRequests = mutableListOf<InstallId>()

    private fun installId(pkgName: String) = InstallId(Pkg.Id(pkgName), testHandle)

    private fun installed(pkgName: String) = mockk<Installed>(relaxed = true).apply {
        every { label } returns pkgName.toCaString()
    }

    private fun timeout() = mockk<AutomationTimeoutException>(relaxed = true)

    private val progressClient = object : Progress.Client {
        override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
            // The loop's progress updates are not what these tests are about.
        }
    }

    private suspend fun runLoop(
        vararg pkgNames: String,
        offLimits: Set<String> = emptySet(),
        missing: Set<String> = emptySet(),
        processOne: suspend (InstallId) -> Unit = { },
    ): AutomationLoopResult {
        val ids = pkgNames.map { installId(it) }
        val known = ids
            .filter { !missing.contains(it.pkgId.name) }
            .associateWith { installed(it.pkgId.name) }
        return progressClient.automateEachTarget(
            targets = ids,
            currentUserHandle = testHandle,
            actionLabel = "Force stopping",
            unsupportedForOtherUsers = "ACS based force-stop is not supported for other users",
            offLimitPkgs = offLimits.map { Pkg.Id(it) }.toSet(),
            resolveOne = { target ->
                resolveRequests.add(target)
                known[target]
            },
            processOne = { installed ->
                val id = known.entries.first { (_, mock) -> mock === installed }.key
                processed.add(id)
                processOne(id)
            },
        )
    }

    @Test
    fun `a processed target is listed as successful`() = runTest2 {
        val target = installId("pkg.a")

        val result = runLoop("pkg.a")

        processed shouldContainExactly listOf(target)
        result.successful shouldContainExactly listOf(target)
        result.failed.keys.shouldBeEmpty()
        result.cancelledByUser shouldBe false
        result.gaveUp shouldBe false
    }

    @Test
    fun `an ordinary failure is listed as failed and the loop continues`() = runTest2 {
        val first = installId("pkg.a")
        val second = installId("pkg.b")

        val result = runLoop("pkg.a", "pkg.b") {
            if (it == first) throw IllegalArgumentException("nope")
        }

        processed shouldContainExactly listOf(first, second)
        result.failed.keys shouldContainExactly listOf(first)
        result.failed.getValue(first).shouldBeInstanceOf<IllegalArgumentException>()
        result.successful shouldContainExactly listOf(second)
        result.gaveUp shouldBe false
    }

    @Test
    fun `a user cancel stops the loop and leaves later targets unresolved`() = runTest2 {
        val first = installId("pkg.a")

        val result = runLoop("pkg.a", "pkg.b", "pkg.c") { throw UserCancelledAutomationException() }

        result.cancelledByUser shouldBe true
        result.gaveUp shouldBe false
        processed shouldContainExactly listOf(first)
        resolveRequests shouldContainExactly listOf(first)
        result.successful.shouldBeEmpty()
        result.failed.keys.shouldBeEmpty()
    }

    @Test
    fun `an invalid system state escapes the loop`() = runTest2 {
        shouldThrow<InvalidSystemStateException> {
            runLoop("pkg.a", "pkg.b") { throw InvalidSystemStateException("no settings app") }
        }
        processed shouldContainExactly listOf(installId("pkg.a"))
    }

    @Test
    fun `an overlay error escapes the loop`() = runTest2 {
        shouldThrow<AutomationOverlayException> {
            runLoop("pkg.a", "pkg.b") { throw mockk<AutomationOverlayException>(relaxed = true) }
        }
        processed shouldContainExactly listOf(installId("pkg.a"))
    }

    @Test
    fun `an unsupported operation escapes the loop`() = runTest2 {
        shouldThrow<UnsupportedOperationException> {
            runLoop("pkg.a", "pkg.b") { throw UnsupportedOperationException("no spec") }
        }
        processed shouldContainExactly listOf(installId("pkg.a"))
    }

    @Test
    fun `an off-limits target is failed without being processed and costs no budget`() = runTest2 {
        val offLimits = installId("pkg.offlimits")
        val pkgNames = listOf("pkg.offlimits") + (1..AUTOMATION_FAILURE_LIMIT).map { "pkg.$it" }
        val attempted = pkgNames.drop(1).map { installId(it) }

        val result = runLoop(
            *pkgNames.toTypedArray(),
            offLimits = setOf("pkg.offlimits"),
        ) { throw timeout() }

        processed shouldContainExactly attempted
        result.failed.keys.toList() shouldContainExactly (listOf(offLimits) + attempted)
        result.failed.getValue(offLimits).shouldBeInstanceOf<IllegalStateException>()
        result.gaveUp shouldBe true
    }

    @Test
    fun `a target missing from the package repo is failed without being processed and costs no budget`() = runTest2 {
        val missing = installId("pkg.gone")
        val pkgNames = listOf("pkg.gone") + (1..AUTOMATION_FAILURE_LIMIT).map { "pkg.$it" }
        val attempted = pkgNames.drop(1).map { installId(it) }

        val result = runLoop(
            *pkgNames.toTypedArray(),
            missing = setOf("pkg.gone"),
        ) { throw timeout() }

        processed shouldContainExactly attempted
        result.failed.keys.toList() shouldContainExactly (listOf(missing) + attempted)
        result.failed.getValue(missing).shouldBeInstanceOf<IllegalStateException>()
        result.failed.getValue(missing).message shouldBe "$missing is not in package repo"
        result.gaveUp shouldBe true
    }

    @Test
    fun `timeouts up to the limit make the loop give up`() = runTest2 {
        val pkgNames = (1..AUTOMATION_FAILURE_LIMIT + 2).map { "pkg.$it" }
        val attempted = pkgNames.take(AUTOMATION_FAILURE_LIMIT).map { installId(it) }

        val result = runLoop(*pkgNames.toTypedArray()) { throw timeout() }

        result.gaveUp shouldBe true
        processed shouldContainExactly attempted
        resolveRequests shouldContainExactly attempted
        result.failed.keys.toList() shouldContainExactly attempted
        result.successful.shouldBeEmpty()
        result.cancelledByUser shouldBe false
    }

    @Test
    fun `unretryable step aborts up to the limit make the loop give up`() = runTest2 {
        val pkgNames = (1..AUTOMATION_FAILURE_LIMIT + 2).map { "pkg.$it" }
        val attempted = pkgNames.take(AUTOMATION_FAILURE_LIMIT).map { installId(it) }

        val result = runLoop(*pkgNames.toTypedArray()) {
            throw StepAbortException("Unreachable", treatAsSuccess = false)
        }

        result.gaveUp shouldBe true
        processed shouldContainExactly attempted
        resolveRequests shouldContainExactly attempted
    }

    @Test
    fun `timeouts and unretryable step aborts share one budget`() = runTest2 {
        val pkgNames = (1..AUTOMATION_FAILURE_LIMIT + 2).map { "pkg.$it" }
        val attempted = pkgNames.take(AUTOMATION_FAILURE_LIMIT).map { installId(it) }

        val result = runLoop(*pkgNames.toTypedArray()) { target ->
            when (processed.size % 2) {
                0 -> throw timeout()
                else -> throw StepAbortException("Unreachable for $target")
            }
        }

        result.gaveUp shouldBe true
        processed shouldContainExactly attempted
    }

    @Test
    fun `one unusable failure short of the limit keeps the loop running`() = runTest2 {
        val pkgNames = (1..AUTOMATION_FAILURE_LIMIT).map { "pkg.$it" }
        val lastName = pkgNames.last()
        val all = pkgNames.map { installId(it) }

        val result = runLoop(*pkgNames.toTypedArray()) { target ->
            if (target.pkgId.name != lastName) throw timeout()
        }

        result.gaveUp shouldBe false
        processed shouldContainExactly all
        result.successful shouldContainExactly listOf(installId(lastName))
    }

    @Test
    fun `successes in between do not reset the budget`() = runTest2 {
        val pkgNames = (1..2 * AUTOMATION_FAILURE_LIMIT + 2).map { "pkg.$it" }
        val attempted = pkgNames.take(2 * AUTOMATION_FAILURE_LIMIT).map { installId(it) }
        val failing = attempted.filterIndexed { index, _ -> index % 2 == 1 }

        val result = runLoop(*pkgNames.toTypedArray()) { target ->
            if (attempted.indexOf(target) % 2 == 1) throw timeout()
        }

        result.gaveUp shouldBe true
        processed shouldContainExactly attempted
        result.failed.keys.toList() shouldContainExactly failing
        result.successful.toList() shouldContainExactly attempted.minus(failing.toSet())
    }

    @Test
    fun `ordinary failures and skips do not count towards the limit`() = runTest2 {
        val unusableNames = (1..AUTOMATION_FAILURE_LIMIT - 1).map { "pkg.$it" }
        val pkgNames = listOf("pkg.offlimits", "pkg.gone", "pkg.broken") + unusableNames + "pkg.last"
        val all = pkgNames.map { installId(it) }

        val result = runLoop(
            *pkgNames.toTypedArray(),
            offLimits = setOf("pkg.offlimits"),
            missing = setOf("pkg.gone"),
        ) { target ->
            when (target.pkgId.name) {
                "pkg.broken" -> throw IllegalStateException("nope")
                in unusableNames -> throw timeout()
                else -> Unit
            }
        }

        result.gaveUp shouldBe false
        processed shouldContainExactly all.minus(setOf(installId("pkg.offlimits"), installId("pkg.gone")))
        result.successful shouldContainExactly listOf(installId("pkg.last"))
    }
}
