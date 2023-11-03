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
        listOf(dupeYoung, dupeOld).sortedWith(
            create().checkDuplicate(ArbiterCriterium.Modified(ArbiterCriterium.Modified.Mode.PREFER_OLDER))
        ) shouldBe listOf(dupeOld, dupeYoung)
        listOf(dupeOld, dupeYoung).sortedWith(
            create().checkDuplicate(ArbiterCriterium.Modified(ArbiterCriterium.Modified.Mode.PREFER_OLDER))
        ) shouldBe listOf(dupeOld, dupeYoung)
    }

    @Test
    fun `check mode - prefer newer`() = runTest {
        listOf(dupeYoung, dupeOld).sortedWith(
            create().checkDuplicate(ArbiterCriterium.Modified(ArbiterCriterium.Modified.Mode.PREFER_NEWER))
        ) shouldBe listOf(dupeYoung, dupeOld)
        listOf(dupeOld, dupeYoung).sortedWith(
            create().checkDuplicate(ArbiterCriterium.Modified(ArbiterCriterium.Modified.Mode.PREFER_NEWER))
        ) shouldBe listOf(dupeYoung, dupeOld)
    }
}