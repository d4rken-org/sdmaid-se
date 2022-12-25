package eu.darken.sdmse.common.files.core

import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.LocalPathLookup
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class APathExtensionTest : BaseTest() {

    @Test
    fun `match operator`() {
        val file1: APath = LocalPath.build("test", "file1")
        val file2: APath = LocalPath.build("test", "file2")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1"),
            fileType = FileType.FILE,
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
        file1.matches(file1) shouldBe true
        file1.matches(file2) shouldBe false
        file1.matches(lookup1) shouldBe true
        file1.matches(lookup2) shouldBe false
        lookup1.matches(file1) shouldBe true
        lookup1.matches(file2) shouldBe false
        lookup1.matches(lookup1) shouldBe true
        lookup1.matches(lookup2) shouldBe false
        file2.matches(lookup2) shouldBe true
    }
}