package eu.darken.sdmse.deduplicator.core.checks

import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import eu.darken.sdmse.deduplicator.core.arbiter.checks.DuplicateTypeCheck
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DuplicateTypeCheckTest : BaseTest() {

    private val dupeChecksum = mockk<Duplicate>().apply {
        every { type } returns Duplicate.Type.CHECKSUM
    }

    private val dupePhash = mockk<Duplicate>().apply {
        every { type } returns Duplicate.Type.PHASH
    }

    private val dupeMedia = mockk<Duplicate>().apply {
        every { type } returns Duplicate.Type.MEDIA
    }

    private fun create() = DuplicateTypeCheck()

    @Test
    fun `check mode - prefer checksum`() = runTest {
        create().favorite(
            listOf(dupeChecksum, dupePhash),
            ArbiterCriterium.DuplicateType(ArbiterCriterium.DuplicateType.Mode.PREFER_CHECKSUM),
        ) shouldBe listOf(dupeChecksum, dupePhash)

        create().favorite(
            listOf(dupePhash, dupeChecksum),
            ArbiterCriterium.DuplicateType(ArbiterCriterium.DuplicateType.Mode.PREFER_CHECKSUM),
        ) shouldBe listOf(dupeChecksum, dupePhash)
    }

    @Test
    fun `check mode - prefer phash`() = runTest {
        create().favorite(
            listOf(dupeChecksum, dupePhash),
            ArbiterCriterium.DuplicateType(ArbiterCriterium.DuplicateType.Mode.PREFER_PHASH),
        ) shouldBe listOf(dupePhash, dupeChecksum)

        create().favorite(
            listOf(dupePhash, dupeChecksum),
            ArbiterCriterium.DuplicateType(ArbiterCriterium.DuplicateType.Mode.PREFER_PHASH),
        ) shouldBe listOf(dupePhash, dupeChecksum)
    }

    @Test
    fun `media hash has same priority as phash`() = runTest {
        // Both similarity types should have equal priority
        val check = create()

        // In PREFER_CHECKSUM mode, checksum comes first, then both similarity types tied
        val preferChecksum = check.favorite(
            listOf(dupeMedia, dupeChecksum, dupePhash),
            ArbiterCriterium.DuplicateType(ArbiterCriterium.DuplicateType.Mode.PREFER_CHECKSUM),
        )
        preferChecksum.first() shouldBe dupeChecksum
    }

    @Test
    fun `prefer checksum with all three types - checksum wins`() = runTest {
        create().favorite(
            listOf(dupePhash, dupeMedia, dupeChecksum),
            ArbiterCriterium.DuplicateType(ArbiterCriterium.DuplicateType.Mode.PREFER_CHECKSUM),
        ).first() shouldBe dupeChecksum
    }

    @Test
    fun `prefer phash with all three types - similarity types before checksum`() = runTest {
        val result = create().favorite(
            listOf(dupeChecksum, dupePhash, dupeMedia),
            ArbiterCriterium.DuplicateType(ArbiterCriterium.DuplicateType.Mode.PREFER_PHASH),
        )
        result.last() shouldBe dupeChecksum
    }
}