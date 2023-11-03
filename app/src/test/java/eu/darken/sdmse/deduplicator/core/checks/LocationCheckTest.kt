package eu.darken.sdmse.deduplicator.core.checks

import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.checks.LocationCheck
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class LocationCheckTest : BaseTest() {

    private val dupeChecksum = mockk<Duplicate>().apply {
        every { type } returns Duplicate.Type.CHECKSUM
    }

    private val dupePhash = mockk<Duplicate>().apply {
        every { type } returns Duplicate.Type.PHASH
    }

    private fun create() = LocationCheck()

    @Test
    fun `check mode - prefer internal storage`() = runTest {
        TODO()
    }

    @Test
    fun `check mode - prefer external storage`() = runTest {
        TODO()
    }
}