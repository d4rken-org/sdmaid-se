package eu.darken.sdmse.deduplicator.core.checks

import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import eu.darken.sdmse.deduplicator.core.arbiter.checks.SizeCheck
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SizeCheckTest : BaseTest() {

    private val dupeSmall = mockk<Duplicate>().apply {
        every { size } returns 10L
    }
    private val dupeLarge = mockk<Duplicate>().apply {
        every { size } returns 9000L
    }

    private fun create() = SizeCheck()

    @Test
    fun `check mode - prefer larger`() = runTest {
        create().favorite(
            listOf(dupeSmall, dupeLarge),
            ArbiterCriterium.Size(ArbiterCriterium.Size.Mode.PREFER_LARGER),
        ) shouldBe listOf(dupeLarge, dupeSmall)

        create().favorite(
            listOf(dupeLarge, dupeSmall),
            ArbiterCriterium.Size(ArbiterCriterium.Size.Mode.PREFER_LARGER),
        ) shouldBe listOf(dupeLarge, dupeSmall)
    }

    @Test
    fun `check mode - prefer smaller`() = runTest {
        create().favorite(
            listOf(dupeSmall, dupeLarge),
            ArbiterCriterium.Size(ArbiterCriterium.Size.Mode.PREFER_SMALLER),
        ) shouldBe listOf(dupeSmall, dupeLarge)

        create().favorite(
            listOf(dupeLarge, dupeSmall),
            ArbiterCriterium.Size(ArbiterCriterium.Size.Mode.PREFER_SMALLER),
        ) shouldBe listOf(dupeSmall, dupeLarge)
    }
}