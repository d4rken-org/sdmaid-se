package eu.darken.sdmse.squeezer.core

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class SqueezerExclusionTest : BaseTest() {

    private fun createImage(
        path: String,
        size: Long = 1024 * 1024L,
    ) = CompressibleImage(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File(path)),
            fileType = FileType.FILE,
            size = size,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        mimeType = CompressibleImage.MIME_TYPE_JPEG,
    )

    @Test
    fun `PathExclusion with SQUEEZER tag is created correctly`() {
        val path = LocalPath.build("/storage/emulated/0/DCIM/Camera/IMG_001.jpg")

        val exclusion = PathExclusion(
            path = path,
            tags = setOf(Exclusion.Tag.SQUEEZER),
        )

        exclusion.path shouldBe path
        exclusion.tags shouldContain Exclusion.Tag.SQUEEZER
        exclusion.tags.size shouldBe 1
    }

    @Test
    fun `PathExclusion id is unique per path`() {
        val path1 = LocalPath.build("/storage/emulated/0/DCIM/Camera/IMG_001.jpg")
        val path2 = LocalPath.build("/storage/emulated/0/DCIM/Camera/IMG_002.jpg")

        val exclusion1 = PathExclusion(path = path1, tags = setOf(Exclusion.Tag.SQUEEZER))
        val exclusion2 = PathExclusion(path = path2, tags = setOf(Exclusion.Tag.SQUEEZER))

        exclusion1.id shouldBe PathExclusion.createId(path1)
        exclusion2.id shouldBe PathExclusion.createId(path2)
        exclusion1.id shouldBe "PathExclusion-${path1.path}"
    }

    @Test
    fun `PathExclusion matches exact path`() = runTest {
        val targetPath = LocalPath.build("/storage/emulated/0/DCIM/Camera/IMG_001.jpg")
        val exclusion = PathExclusion(path = targetPath, tags = setOf(Exclusion.Tag.SQUEEZER))

        exclusion.match(targetPath) shouldBe true
    }

    @Test
    fun `PathExclusion matches child paths when excluding directory`() = runTest {
        val dirPath = LocalPath.build("/storage/emulated/0/DCIM/Camera")
        val childPath = LocalPath.build("/storage/emulated/0/DCIM/Camera/IMG_001.jpg")
        val exclusion = PathExclusion(path = dirPath, tags = setOf(Exclusion.Tag.SQUEEZER))

        exclusion.match(childPath) shouldBe true
    }

    @Test
    fun `PathExclusion does not match unrelated paths`() = runTest {
        val targetPath = LocalPath.build("/storage/emulated/0/DCIM/Camera/IMG_001.jpg")
        val unrelatedPath = LocalPath.build("/storage/emulated/0/Pictures/photo.jpg")
        val exclusion = PathExclusion(path = targetPath, tags = setOf(Exclusion.Tag.SQUEEZER))

        exclusion.match(unrelatedPath) shouldBe false
    }

    @Test
    fun `PathExclusion does not match parent paths`() = runTest {
        val childPath = LocalPath.build("/storage/emulated/0/DCIM/Camera/IMG_001.jpg")
        val parentPath = LocalPath.build("/storage/emulated/0/DCIM")
        val exclusion = PathExclusion(path = childPath, tags = setOf(Exclusion.Tag.SQUEEZER))

        // Excluding a file should not exclude its parent directory
        exclusion.match(parentPath) shouldBe false
    }

    @Test
    fun `SQUEEZER tag exists in Exclusion Tag enum`() {
        val squeezerTag = Exclusion.Tag.SQUEEZER

        squeezerTag.name shouldBe "SQUEEZER"
    }

    @Test
    fun `exclusion from image path creates correct PathExclusion`() {
        val image = createImage("/storage/emulated/0/DCIM/Camera/IMG_001.jpg")

        // Simulating what Squeezer.exclude() does
        val exclusion = PathExclusion(
            path = image.path,
            tags = setOf(Exclusion.Tag.SQUEEZER),
        )

        exclusion.path.path shouldBe "/storage/emulated/0/DCIM/Camera/IMG_001.jpg"
        exclusion.tags shouldContain Exclusion.Tag.SQUEEZER
    }

    @Test
    fun `multiple images create distinct exclusions`() {
        val image1 = createImage("/storage/emulated/0/DCIM/Camera/IMG_001.jpg")
        val image2 = createImage("/storage/emulated/0/DCIM/Camera/IMG_002.jpg")
        val image3 = createImage("/storage/emulated/0/Pictures/photo.jpg")

        val exclusions = listOf(image1, image2, image3).map { image ->
            PathExclusion(
                path = image.path,
                tags = setOf(Exclusion.Tag.SQUEEZER),
            )
        }.toSet()

        exclusions.size shouldBe 3
        exclusions.map { it.path.path } shouldBe listOf(
            "/storage/emulated/0/DCIM/Camera/IMG_001.jpg",
            "/storage/emulated/0/DCIM/Camera/IMG_002.jpg",
            "/storage/emulated/0/Pictures/photo.jpg",
        )
    }

    @Test
    fun `exclusion label is a CaString`() {
        val path = LocalPath.build("/storage/emulated/0/DCIM/Camera/IMG_001.jpg")
        val exclusion = PathExclusion(path = path, tags = setOf(Exclusion.Tag.SQUEEZER))

        // Label should be a CaString (context-aware string)
        exclusion.label shouldNotBe null
    }

    @Test
    fun `PathExclusion with SQUEEZER tag differs from GENERAL tag`() {
        val path = LocalPath.build("/storage/emulated/0/DCIM/test.jpg")

        val squeezerExclusion = PathExclusion(path = path, tags = setOf(Exclusion.Tag.SQUEEZER))
        val generalExclusion = PathExclusion(path = path, tags = setOf(Exclusion.Tag.GENERAL))

        squeezerExclusion.tags shouldBe setOf(Exclusion.Tag.SQUEEZER)
        generalExclusion.tags shouldBe setOf(Exclusion.Tag.GENERAL)
    }
}
