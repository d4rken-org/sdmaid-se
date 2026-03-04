package eu.darken.sdmse.deduplicator.core.checks

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import eu.darken.sdmse.deduplicator.core.arbiter.checks.LocationCheck
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class LocationCheckTest : BaseTest() {

    private val pathIntern = LocalPath.build("intern")
    private val dupeIntern = mockk<Duplicate>().apply {
        every { path } returns pathIntern
    }

    private val pathExtern = LocalPath.build("extern")
    private val dupeExtern = mockk<Duplicate>().apply {
        every { path } returns pathExtern
    }
    private val fileForensics = mockk<FileForensics>().apply {
        coEvery { identifyArea(pathIntern) } returns mockk<AreaInfo>().apply {
            every { dataArea } returns mockk<DataArea>().apply {
                every { flags } returns setOf(DataArea.Flag.PRIMARY)
            }
        }
        coEvery { identifyArea(pathExtern) } returns mockk<AreaInfo>().apply {
            every { dataArea } returns mockk<DataArea>().apply {
                every { flags } returns emptySet()
            }
        }
    }

    private fun create() = LocationCheck(
        forensics = fileForensics
    )

    @Test
    fun `check mode - prefer internal storage`() = runTest {
        create().favorite(
            listOf(dupeIntern, dupeExtern),
            ArbiterCriterium.Location(ArbiterCriterium.Location.Mode.PREFER_PRIMARY),
        ) shouldBe listOf(dupeIntern, dupeExtern)

        create().favorite(
            listOf(dupeExtern, dupeIntern),
            ArbiterCriterium.Location(ArbiterCriterium.Location.Mode.PREFER_PRIMARY),
        ) shouldBe listOf(dupeIntern, dupeExtern)
    }

    @Test
    fun `check mode - prefer external storage`() = runTest {
        create().favorite(
            listOf(dupeIntern, dupeExtern),
            ArbiterCriterium.Location(ArbiterCriterium.Location.Mode.PREFER_SECONDARY),
        ) shouldBe listOf(dupeExtern, dupeIntern)

        create().favorite(
            listOf(dupeExtern, dupeIntern),
            ArbiterCriterium.Location(ArbiterCriterium.Location.Mode.PREFER_SECONDARY),
        ) shouldBe listOf(dupeExtern, dupeIntern)
    }
}