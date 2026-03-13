package eu.darken.sdmse.deduplicator.core.checks

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import eu.darken.sdmse.deduplicator.core.arbiter.checks.PreferredPathCheck
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PreferredPathCheckTest : BaseTest() {

    private fun create() = PreferredPathCheck()

    private val dupeInDownloads = mockk<Duplicate>().apply {
        every { path } returns LocalPath.build("/storage/emulated/0/Download/file.txt")
    }
    private val dupeInAppFolder = mockk<Duplicate>().apply {
        every { path } returns LocalPath.build("/storage/emulated/0/Android/data/com.app/files/file.txt")
    }
    private val dupeInPictures = mockk<Duplicate>().apply {
        every { path } returns LocalPath.build("/storage/emulated/0/Pictures/file.txt")
    }

    @Test
    fun `empty config returns list unchanged`() = runTest {
        val input = listOf(dupeInDownloads, dupeInAppFolder)
        create().favorite(
            input,
            ArbiterCriterium.PreferredPath(emptySet()),
        ) shouldBe input
    }

    @Test
    fun `files in configured paths sort first - kept`() = runTest {
        val picturesPath = LocalPath.build("/storage/emulated/0/Pictures")

        // dupeInPictures is in the keep-prefer path, so it should sort first (kept)
        create().favorite(
            listOf(dupeInDownloads, dupeInPictures),
            ArbiterCriterium.PreferredPath(setOf(picturesPath)),
        ) shouldBe listOf(dupeInPictures, dupeInDownloads)

        // Order of input shouldn't matter
        create().favorite(
            listOf(dupeInPictures, dupeInDownloads),
            ArbiterCriterium.PreferredPath(setOf(picturesPath)),
        ) shouldBe listOf(dupeInPictures, dupeInDownloads)
    }

    @Test
    fun `files outside configured paths sort last - deleted`() = runTest {
        val picturesPath = LocalPath.build("/storage/emulated/0/Pictures")

        // dupeInDownloads and dupeInAppFolder are NOT in keep-prefer path
        // They should both sort after dupeInPictures
        val result = create().favorite(
            listOf(dupeInDownloads, dupeInAppFolder, dupeInPictures),
            ArbiterCriterium.PreferredPath(setOf(picturesPath)),
        )

        // dupeInPictures should be first (kept)
        result.first() shouldBe dupeInPictures
        // dupeInDownloads and dupeInAppFolder should be after dupeInPictures
        result.drop(1).toSet() shouldBe setOf(dupeInDownloads, dupeInAppFolder)
    }

    @Test
    fun `multiple configured paths handled correctly`() = runTest {
        val downloadPath = LocalPath.build("/storage/emulated/0/Download")
        val picturesPath = LocalPath.build("/storage/emulated/0/Pictures")

        // Both dupeInDownloads and dupeInPictures are in keep-prefer paths
        val result = create().favorite(
            listOf(dupeInDownloads, dupeInAppFolder, dupeInPictures),
            ArbiterCriterium.PreferredPath(setOf(downloadPath, picturesPath)),
        )

        // dupeInAppFolder should be last (deleted)
        result.last() shouldBe dupeInAppFolder
    }

    @Test
    fun `nested paths handled correctly`() = runTest {
        val parentPath = LocalPath.build("/storage/emulated/0")

        // All files are under the parent path
        val result = create().favorite(
            listOf(dupeInDownloads, dupeInAppFolder, dupeInPictures),
            ArbiterCriterium.PreferredPath(setOf(parentPath)),
        )

        // All files should be in keep-prefer path (sort first), so relative order is stable
        result.size shouldBe 3
    }

    @Test
    fun `exact path match handled correctly`() = runTest {
        val exactPath = LocalPath.build("/storage/emulated/0/Pictures/file.txt")

        create().favorite(
            listOf(dupeInDownloads, dupeInPictures),
            ArbiterCriterium.PreferredPath(setOf(exactPath)),
        ) shouldBe listOf(dupeInPictures, dupeInDownloads)
    }
}
