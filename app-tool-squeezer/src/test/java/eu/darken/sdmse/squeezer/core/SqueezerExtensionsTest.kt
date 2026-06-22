package eu.darken.sdmse.squeezer.core

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class SqueezerExtensionsTest : BaseTest() {

    private fun image(path: String) = CompressibleImage(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File(path)),
            fileType = FileType.FILE,
            size = 1024L,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        mimeType = CompressibleImage.MIME_TYPE_JPEG,
    )

    @Test
    fun `hasData is false when Data is null`() {
        val data: Squeezer.Data? = null
        data.hasData shouldBe false
    }

    @Test
    fun `hasData is false when media is empty`() {
        val data = Squeezer.Data(media = emptySet())
        data.hasData shouldBe false
    }

    @Test
    fun `hasData is true when media is non-empty`() {
        val data = Squeezer.Data(media = setOf(image("a.jpg")))
        data.hasData shouldBe true
    }
}
