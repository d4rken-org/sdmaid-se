package eu.darken.sdmse.deduplicator.core.scanner

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.media.MediaDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHashBits
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHasher
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StripCoveredPathsTest : BaseTest() {

    private fun lp(path: String) = LocalPath.build(path)

    private fun createPHashGroup(vararg paths: String): PHashDuplicate.Group {
        return PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("phash-group"),
            duplicates = paths.map { path ->
                PHashDuplicate(
                    lookup = mockk {
                        every { lookedUp } returns lp(path)
                        every { this@mockk.path } returns path
                    },
                    hash = PHasher.Result(hash = PHashBits(0L)),
                    similarity = 1.0,
                )
            }.toSet(),
        )
    }

    private fun createMediaGroup(vararg paths: String): MediaDuplicate.Group {
        return MediaDuplicate.Group(
            identifier = Duplicate.Group.Id("media-group"),
            duplicates = paths.map { path ->
                MediaDuplicate(
                    lookup = mockk {
                        every { lookedUp } returns lp(path)
                        every { this@mockk.path } returns path
                    },
                    audioHash = null,
                    frameHashes = emptyList(),
                    similarity = 1.0,
                )
            }.toSet(),
        )
    }

    @Test
    fun `phash group - strips covered paths`() {
        val group = createPHashGroup("/a.jpg", "/b.jpg", "/c.jpg")
        val covered = setOf(lp("/a.jpg"))

        val result = group.stripCoveredPaths(covered)

        result.shouldNotBeNull()
        result.duplicates.size shouldBe 2
    }

    @Test
    fun `media group - strips covered paths`() {
        val group = createMediaGroup("/a.mp4", "/b.mp4", "/c.mp4")
        val covered = setOf(lp("/b.mp4"))

        val result = group.stripCoveredPaths(covered)

        result.shouldNotBeNull()
        result.duplicates.size shouldBe 2
    }

    @Test
    fun `all paths covered - returns null`() {
        val group = createPHashGroup("/a.jpg", "/b.jpg")
        val covered = setOf(lp("/a.jpg"), lp("/b.jpg"))

        group.stripCoveredPaths(covered).shouldBeNull()
    }

    @Test
    fun `no paths covered - returns full group`() {
        val group = createMediaGroup("/a.mp4", "/b.mp4")
        val covered = setOf(lp("/unrelated.mp4"))

        val result = group.stripCoveredPaths(covered)

        result.shouldNotBeNull()
        result.duplicates.size shouldBe 2
    }

    @Test
    fun `checksum group type - returns null`() {
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("checksum-group"),
            duplicates = setOf(
                mockk {
                    every { path } returns mockk()
                    every { lookup } returns mockk()
                },
            ),
        )

        group.stripCoveredPaths(emptySet()).shouldBeNull()
    }
}
