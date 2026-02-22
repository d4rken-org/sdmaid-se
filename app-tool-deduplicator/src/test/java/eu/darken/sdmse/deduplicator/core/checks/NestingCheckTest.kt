package eu.darken.sdmse.deduplicator.core.checks

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import eu.darken.sdmse.deduplicator.core.arbiter.checks.NestingCheck
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class NestingCheckTest : BaseTest() {

    private fun create() = NestingCheck()

    private val dupeShallow = mockk<Duplicate>().apply {
        every { path } returns LocalPath.build("/seg1/seg2")
    }
    private val dupeDeep = mockk<Duplicate>().apply {
        every { path } returns LocalPath.build("/seg1/seg2/seg3/seg4")
    }

    @Test
    fun `check mode - prefer deeper`() = runTest {
        create().favorite(
            listOf(dupeShallow, dupeDeep),
            ArbiterCriterium.Nesting(ArbiterCriterium.Nesting.Mode.PREFER_DEEPER),
        ) shouldBe listOf(dupeDeep, dupeShallow)

        create().favorite(
            listOf(dupeDeep, dupeShallow),
            ArbiterCriterium.Nesting(ArbiterCriterium.Nesting.Mode.PREFER_DEEPER),
        ) shouldBe listOf(dupeDeep, dupeShallow)
    }

    @Test
    fun `check mode - prefer shallow`() = runTest {
        create().favorite(
            listOf(dupeShallow, dupeDeep),
            ArbiterCriterium.Nesting(ArbiterCriterium.Nesting.Mode.PREFER_SHALLOW),
        ) shouldBe listOf(dupeShallow, dupeDeep)

        create().favorite(
            listOf(dupeDeep, dupeShallow),
            ArbiterCriterium.Nesting(ArbiterCriterium.Nesting.Mode.PREFER_SHALLOW),
        ) shouldBe listOf(dupeShallow, dupeDeep)
    }
}