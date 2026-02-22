package eu.darken.sdmse.deduplicator.core.checks

import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import eu.darken.sdmse.deduplicator.core.arbiter.checks.ModificationCheck
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class ModificationCheckTest : BaseTest() {

    private val dupeYoung = mockk<Duplicate>().apply {
        every { modifiedAt } returns Instant.MAX
    }

    private val dupeOld = mockk<Duplicate>().apply {
        every { modifiedAt } returns Instant.MIN
    }

    private fun create() = ModificationCheck()

    @Test
    fun `check mode - prefer older`() = runTest {
        create().favorite(
            listOf(dupeYoung, dupeOld),
            ArbiterCriterium.Modified(ArbiterCriterium.Modified.Mode.PREFER_OLDER),
        ) shouldBe listOf(dupeOld, dupeYoung)

        create().favorite(
            listOf(dupeOld, dupeYoung),
            ArbiterCriterium.Modified(ArbiterCriterium.Modified.Mode.PREFER_OLDER),
        ) shouldBe listOf(dupeOld, dupeYoung)
    }

    @Test
    fun `check mode - prefer newer`() = runTest {
        create().favorite(
            listOf(dupeYoung, dupeOld),
            ArbiterCriterium.Modified(ArbiterCriterium.Modified.Mode.PREFER_NEWER),
        ) shouldBe listOf(dupeYoung, dupeOld)

        create().favorite(
            listOf(dupeOld, dupeYoung),
            ArbiterCriterium.Modified(ArbiterCriterium.Modified.Mode.PREFER_NEWER),
        ) shouldBe listOf(dupeYoung, dupeOld)
    }
}