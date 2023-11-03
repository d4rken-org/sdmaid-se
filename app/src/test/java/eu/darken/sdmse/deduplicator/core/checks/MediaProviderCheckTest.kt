package eu.darken.sdmse.deduplicator.core.checks

import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.checks.MediaProviderCheck
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class MediaProviderCheckTest : BaseTest() {

    private val dupeChecksum = mockk<Duplicate>().apply {
        every { type } returns Duplicate.Type.CHECKSUM
    }

    private val dupePhash = mockk<Duplicate>().apply {
        every { type } returns Duplicate.Type.PHASH
    }

    private fun create() = MediaProviderCheck()

    @Test
    fun `check mode - prefer indexed`() = runTest {
        TODO()
    }

    @Test
    fun `check mode - prefer not indexed`() = runTest {
        TODO()
    }
}