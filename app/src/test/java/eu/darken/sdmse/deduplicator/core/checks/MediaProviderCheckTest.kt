package eu.darken.sdmse.deduplicator.core.checks

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import eu.darken.sdmse.deduplicator.core.arbiter.checks.MediaProviderCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.MediaStoreTool
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class MediaProviderCheckTest : BaseTest() {

    private val pathIndexed = LocalPath.build("indexed")
    private val dupeIndexed = mockk<Duplicate>().apply {
        every { path } returns pathIndexed
    }

    private val pathUnknown = LocalPath.build("unknown")
    private val dupeUnknown = mockk<Duplicate>().apply {
        every { path } returns pathUnknown
    }

    private val mediaStoreTool = mockk<MediaStoreTool>().apply {
        coEvery { isIndexed(pathIndexed) } returns true
        coEvery { isIndexed(pathUnknown) } returns false
    }

    private fun create() = MediaProviderCheck(
        mediaStoreTool
    )

    @Test
    fun `check mode - prefer indexed`() = runTest {
        create().favorite(
            listOf(dupeIndexed, dupeUnknown),
            ArbiterCriterium.MediaProvider(ArbiterCriterium.MediaProvider.Mode.PREFER_INDEXED),
        ) shouldBe listOf(dupeIndexed, dupeUnknown)

        create().favorite(
            listOf(dupeUnknown, dupeIndexed),
            ArbiterCriterium.MediaProvider(ArbiterCriterium.MediaProvider.Mode.PREFER_INDEXED),
        ) shouldBe listOf(dupeIndexed, dupeUnknown)
    }

    @Test
    fun `check mode - prefer not indexed`() = runTest {
        create().favorite(
            listOf(dupeIndexed, dupeUnknown),
            ArbiterCriterium.MediaProvider(ArbiterCriterium.MediaProvider.Mode.PREFER_UNKNOWN),
        ) shouldBe listOf(dupeUnknown, dupeIndexed)

        create().favorite(
            listOf(dupeUnknown, dupeIndexed),
            ArbiterCriterium.MediaProvider(ArbiterCriterium.MediaProvider.Mode.PREFER_UNKNOWN),
        ) shouldBe listOf(dupeUnknown, dupeIndexed)
    }
}