package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.IOException

/**
 * The withholding contract of [CorpseFilter.untestedApiCeiling], independent of any concrete filter:
 * past the ceiling a filter still scans, but its findings never leave [CorpseFilter.scan].
 */
class CorpseFilterWithholdingTest : BaseTest() {

    private val corpses = listOf(previewCorpse(), previewCorpse())
    private var fakeSdkLevel: Int = 0

    @BeforeEach fun setup() {
        mockkStatic("eu.darken.sdmse.common.BuildWrapKt")
        every { hasApiLevel(any()) } answers { fakeSdkLevel >= firstArg<Int>() }
    }

    @AfterEach fun cleanup() {
        unmockkAll()
    }

    private class TestFilter(
        override val untestedApiCeiling: Int?,
        private val produces: Collection<Corpse>,
        private val fails: (() -> Throwable)? = null,
    ) : CorpseFilter("TestFilter", Progress.Data()) {
        var scans: Int = 0
            private set

        override suspend fun doScan(): Collection<Corpse> {
            scans++
            fails?.let { throw it() }
            return produces
        }
    }

    private fun filter(ceiling: Int?, produces: Collection<Corpse> = corpses) = TestFilter(ceiling, produces)

    private fun failingFilter(ceiling: Int?, error: () -> Throwable) = TestFilter(ceiling, emptySet(), error)

    @Test fun `corpses are withheld at the ceiling`() = runTest2 {
        fakeSdkLevel = 37
        val filter = filter(ceiling = 37)

        filter.scan() shouldBe emptySet()
        // Withholding is about the findings, the scan itself still ran
        filter.scans shouldBe 1
    }

    @Test fun `corpses are withheld above the ceiling`() = runTest2 {
        fakeSdkLevel = 42
        val filter = filter(ceiling = 37)

        filter.scan() shouldBe emptySet()
        filter.scans shouldBe 1
    }

    @Test fun `corpses are reported below the ceiling`() = runTest2 {
        fakeSdkLevel = 36
        val filter = filter(ceiling = 37)

        filter.scan() shouldContainExactlyInAnyOrder corpses
    }

    @Test fun `a filter without a ceiling always reports`() = runTest2 {
        fakeSdkLevel = 999
        val filter = filter(ceiling = null)

        filter.scan() shouldContainExactlyInAnyOrder corpses
    }

    @Test fun `withholding an empty scan stays empty`() = runTest2 {
        fakeSdkLevel = 37
        val filter = filter(ceiling = 37, produces = emptySet())

        filter.scan() shouldBe emptySet()
        filter.scans shouldBe 1
    }

    /**
     * Past the ceiling the scan runs against an Android version nobody validated it on, so a
     * failure there must not turn a scan that used to end quietly into a failed task.
     */
    @Test fun `a failure past the ceiling is swallowed`() = runTest2 {
        fakeSdkLevel = 37
        val filter = failingFilter(ceiling = 37) { IOException("nope") }

        filter.scan() shouldBe emptySet()
        filter.scans shouldBe 1
    }

    @Test fun `a failure below the ceiling still propagates`() = runTest2 {
        fakeSdkLevel = 36
        val filter = failingFilter(ceiling = 37) { IOException("nope") }

        shouldThrow<IOException> { filter.scan() }
    }

    @Test fun `cancellation past the ceiling is never swallowed`() = runTest2 {
        fakeSdkLevel = 37
        val filter = failingFilter(ceiling = 37) { CancellationException("stop") }

        shouldThrow<CancellationException> { filter.scan() }
    }

    /**
     * Cancellation doesn't always arrive as a CancellationException. LocalGateway catches every
     * Exception its operations throw, cancellation included, and rethrows it as a ReadException, so
     * swallowing on the exception type alone would drop a cancellation on the floor.
     */
    @Test fun `a failure delivered after cancellation is not swallowed`() = runTest2 {
        fakeSdkLevel = 37
        val filter = object : CorpseFilter("TestFilter", Progress.Data()) {
            override val untestedApiCeiling: Int = 37
            override suspend fun doScan(): Collection<Corpse> {
                currentCoroutineContext().cancel(CancellationException("cancelled mid-scan"))
                throw IOException("cancellation, wrapped by the gateway")
            }
        }

        shouldThrow<CancellationException> {
            coroutineScope { filter.scan() }
        }
    }
}
