package eu.darken.sdmse.common.files

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
    private val treeUri = "content://com.android.externalstorage.documents/tree/primary%3A"

    @Test fun `file type checks`() {
        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1"),
            fileType = FileType.DIRECTORY,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file2"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
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

    @Test fun `filterDistinctRoots operator`() {
        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1"),
            fileType = FileType.DIRECTORY,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup1s: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1", "sub"),
            fileType = FileType.DIRECTORY,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "test", "file2"),
            docFile = mockk<SAFDocFile>().apply {
                every { isFile } returns true
                every { isDirectory } returns false
            }
        )
        val lookup2s: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "test", "file2", "sub"),
            docFile = mockk<SAFDocFile>().apply {
                every { isFile } returns true
                every { isDirectory } returns false
            }
        )
        setOf(lookup1, lookup1s, lookup2, lookup2s).filterDistinctRoots() shouldBe setOf(lookup1, lookup2)
    }

    @Test fun `filterDistinctRoots operator - edge case`() {
        /*
        0 = {LocalPathLookup@31360} LocalPathLookup(lookedUp=LocalPath(/data/log/knoxsdk.log.0.lck), fileType=FILE, size=0, modifiedAt=2023-01-04T21:34:36Z, target=null)
        1 = {LocalPathLookup@31361} LocalPathLookup(lookedUp=LocalPath(/data/log/knoxsdk.log.0), fileType=FILE, size=7920, modifiedAt=2023-06-27T05:53:04Z, target=null)
        2 = {LocalPathLookup@31365} LocalPathLookup(lookedUp=LocalPath(/data/log/knoxsdk.log.0.1.lck), fileType=FILE, size=0, modifiedAt=2023-01-04T21:34:41Z, target=null)
        3 = {LocalPathLookup@31366} LocalPathLookup(lookedUp=LocalPath(/data/log/knoxsdk.log.0.1), fileType=FILE, size=2660, modifiedAt=2023-06-27T05:53:09Z, target=null)
         */
        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("data", "log", "knoxsdk.log.0.lck"),
            fileType = FileType.FILE,
            size = 0,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("data", "log", "knoxsdk.log.0"),
            fileType = FileType.FILE,
            size = 7920,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup3: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("data", "log", "knoxsdk.log.0.1.lck"),
            fileType = FileType.FILE,
            size = 0,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup4: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("data", "log", "knoxsdk.log.0.1"),
            fileType = FileType.FILE,
            size = 2660,
            modifiedAt = Instant.EPOCH,
            target = null,
        )

        listOf(
            lookup1,
            lookup2,
            lookup3,
            lookup4
        ).filterDistinctRoots() shouldBe setOf(
            lookup1,
            lookup2,
            lookup3,
            lookup4
        )
    }
}
