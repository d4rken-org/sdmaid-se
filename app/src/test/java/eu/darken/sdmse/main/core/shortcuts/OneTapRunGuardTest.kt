package eu.darken.sdmse.main.core.shortcuts

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class OneTapRunGuardTest : BaseTest() {

    @Test
    fun `tryStart claims once then blocks a second caller until finished`() {
        val guard = OneTapRunGuard()
        guard.running.value shouldBe false

        guard.tryStart(Job()) shouldBe true    // first caller wins the run
        guard.running.value shouldBe true

        guard.tryStart(Job()) shouldBe false   // second concurrent caller is rejected
        guard.running.value shouldBe true

        guard.finish()
        guard.running.value shouldBe false

        guard.tryStart(Job()) shouldBe true    // claimable again after the run finished
        guard.running.value shouldBe true
    }

    @Test
    fun `cancelRun cancels the registered run job`() {
        val guard = OneTapRunGuard()
        val job = Job()

        guard.tryStart(job) shouldBe true
        job.isCancelled shouldBe false

        guard.cancelRun()
        job.isCancelled shouldBe true
        // running stays true until the run's finally calls finish()
        guard.running.value shouldBe true

        guard.finish()
        guard.running.value shouldBe false
    }

    @Test
    fun `cancelRun while idle is a no-op`() {
        val guard = OneTapRunGuard()
        guard.cancelRun()
        guard.running.value shouldBe false

        // a job from a FINISHED run must not be cancelled retroactively
        val job = Job()
        guard.tryStart(job) shouldBe true
        guard.finish()
        guard.cancelRun()
        job.isCancelled shouldBe false
    }
}
