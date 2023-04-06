package eu.darken.sdmse.common.files

import android.net.Uri
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.saf.SAFDocFile
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.files.saf.SAFPathLookup
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class APathLookupExtensionTest : BaseTest() {
    private val treeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")

    @Test fun `file type checks`() {
        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1"),
            fileType = FileType.DIRECTORY,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file2"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        lookup1.isDirectory shouldBe true
        lookup1.isFile shouldBe false
        lookup2.isFile shouldBe true
        lookup2.isDirectory shouldBe false
    }

    @Test fun `match operator - SAFPath`() {
        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "test", "file1"),
            docFile = mockk<SAFDocFile>().apply {
                every { isDirectory } returns true
                every { isFile } returns false
            }
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "test", "file2"),
            docFile = mockk<SAFDocFile>().apply {
                every { isFile } returns true
                every { isDirectory } returns false
            }
        )
        lookup1.isDirectory shouldBe true
        lookup1.isFile shouldBe false
        lookup2.isFile shouldBe true
        lookup2.isDirectory shouldBe false
    }
}
