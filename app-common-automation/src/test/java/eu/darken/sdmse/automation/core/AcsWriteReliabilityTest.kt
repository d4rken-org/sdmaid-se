package eu.darken.sdmse.automation.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.BuildWrap
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.runTest2

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AcsWriteReliabilityTest : BaseTest() {

    private lateinit var settings: AutomationSettings

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settings = AutomationSettings(context, Json { })
        mockkObject(BuildWrap)
    }

    @After fun teardown() {
        unmockkObject(BuildWrap)
    }

    @Test fun `fresh state does not avoid the direct write`() = runTest2 {
        every { BuildWrap.FINGERPRINT } returns "build/fp-1"
        AcsWriteReliability(settings).shouldAvoidDirectWrite() shouldBe false
    }

    @Test fun `marking unreliable makes the same build avoid the direct write`() = runTest2 {
        every { BuildWrap.FINGERPRINT } returns "build/fp-1"
        val reliability = AcsWriteReliability(settings)

        reliability.markDirectWriteUnreliable()

        reliability.shouldAvoidDirectWrite() shouldBe true
    }

    @Test fun `an OTA (changed fingerprint) re-probes the direct write`() = runTest2 {
        val reliability = AcsWriteReliability(settings)

        every { BuildWrap.FINGERPRINT } returns "build/fp-1"
        reliability.markDirectWriteUnreliable()
        reliability.shouldAvoidDirectWrite() shouldBe true

        // OTA: fingerprint changes, so the learned marker no longer applies.
        every { BuildWrap.FINGERPRINT } returns "build/fp-2"
        reliability.shouldAvoidDirectWrite() shouldBe false
    }
}
