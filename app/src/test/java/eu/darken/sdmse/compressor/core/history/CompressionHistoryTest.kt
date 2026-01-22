package eu.darken.sdmse.compressor.core.history

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.security.MessageDigest
import java.time.Instant

class CompressionHistoryTest : BaseTest() {

    /**
     * Replicate the path-to-hash logic from CompressionHistoryDatabase for testing.
     */
    private fun pathToHash(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(path.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `path hashing produces consistent results`() {
        val path = "/storage/emulated/0/DCIM/Camera/IMG_20240101_120000.jpg"

        val hash1 = pathToHash(path)
        val hash2 = pathToHash(path)

        hash1 shouldBe hash2
    }

    @Test
    fun `different paths produce different hashes`() {
        val path1 = "/storage/emulated/0/DCIM/Camera/IMG_001.jpg"
        val path2 = "/storage/emulated/0/DCIM/Camera/IMG_002.jpg"

        val hash1 = pathToHash(path1)
        val hash2 = pathToHash(path2)

        hash1 shouldNotBe hash2
    }

    @Test
    fun `hash is SHA-256 format`() {
        val path = "/test/path.jpg"
        val hash = pathToHash(path)

        // SHA-256 produces 64 hex characters
        hash.length shouldBe 64
        hash.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
    }

    @Test
    fun `CompressionHistoryEntity stores correct data`() {
        val entity = CompressionHistoryEntity(
            pathHash = "abc123",
            path = "/storage/emulated/0/DCIM/Camera/test.jpg",
            originalSize = 5_000_000L,
            compressedSize = 3_000_000L,
            quality = 80,
            compressedAt = Instant.parse("2024-01-01T12:00:00Z"),
        )

        entity.pathHash shouldBe "abc123"
        entity.path shouldBe "/storage/emulated/0/DCIM/Camera/test.jpg"
        entity.originalSize shouldBe 5_000_000L
        entity.compressedSize shouldBe 3_000_000L
        entity.quality shouldBe 80
        entity.compressedAt shouldBe Instant.parse("2024-01-01T12:00:00Z")
    }

    @Test
    fun `entity calculates correct savings`() {
        val entity = CompressionHistoryEntity(
            pathHash = "abc123",
            path = "/test.jpg",
            originalSize = 10_000_000L,
            compressedSize = 6_500_000L,
            quality = 80,
            compressedAt = Instant.now(),
        )

        val savings = entity.originalSize - entity.compressedSize

        savings shouldBe 3_500_000L
    }

    @Test
    fun `entity calculates savings percentage`() {
        val entity = CompressionHistoryEntity(
            pathHash = "abc123",
            path = "/test.jpg",
            originalSize = 10_000_000L,
            compressedSize = 6_500_000L,
            quality = 80,
            compressedAt = Instant.now(),
        )

        val savings = entity.originalSize - entity.compressedSize
        val savingsPercent = (savings.toDouble() / entity.originalSize.toDouble() * 100).toInt()

        savingsPercent shouldBe 35 // 35% saved
    }

    @Test
    fun `entity handles zero original size gracefully`() {
        val entity = CompressionHistoryEntity(
            pathHash = "abc123",
            path = "/test.jpg",
            originalSize = 0L,
            compressedSize = 0L,
            quality = 80,
            compressedAt = Instant.now(),
        )

        val savings = entity.originalSize - entity.compressedSize

        savings shouldBe 0L
    }

    @Test
    fun `path with special characters hashes correctly`() {
        val path = "/storage/emulated/0/DCIM/Camera/Photo 2024-01-01 12:00:00.jpg"

        val hash = pathToHash(path)

        // Should produce a valid hash without errors
        hash.length shouldBe 64
    }

    @Test
    fun `unicode path hashes correctly`() {
        val path = "/storage/emulated/0/DCIM/相机/照片.jpg"

        val hash = pathToHash(path)

        hash.length shouldBe 64
    }

    // === Hash Lookup Tests ===

    @Test
    fun `hash lookup in Set works correctly`() {
        val path1 = "/storage/emulated/0/DCIM/Camera/IMG_001.jpg"
        val path2 = "/storage/emulated/0/DCIM/Camera/IMG_002.jpg"
        val path3 = "/storage/emulated/0/DCIM/Camera/IMG_003.jpg"

        val compressedHashes = setOf(
            pathToHash(path1),
            pathToHash(path2),
        )

        // path1 and path2 should be found, path3 should not
        (pathToHash(path1) in compressedHashes) shouldBe true
        (pathToHash(path2) in compressedHashes) shouldBe true
        (pathToHash(path3) in compressedHashes) shouldBe false
    }

    @Test
    fun `hash uniqueness across many paths`() {
        val paths = (1..100).map { "/storage/emulated/0/DCIM/Camera/IMG_$it.jpg" }
        val hashes = paths.map { pathToHash(it) }.toSet()

        // All hashes should be unique
        hashes.size shouldBe 100
    }

    @Test
    fun `case sensitivity in path hashing`() {
        val path1 = "/storage/emulated/0/DCIM/Camera/IMG.jpg"
        val path2 = "/storage/emulated/0/DCIM/Camera/img.jpg"

        val hash1 = pathToHash(path1)
        val hash2 = pathToHash(path2)

        // Different case = different hash
        hash1 shouldNotBe hash2
    }

    @Test
    fun `empty path hashes without error`() {
        val hash = pathToHash("")

        hash.length shouldBe 64
    }

    @Test
    fun `very long path hashes correctly`() {
        val longPath = "/storage/emulated/0/DCIM" + "/subdirectory".repeat(50) + "/photo.jpg"

        val hash = pathToHash(longPath)

        hash.length shouldBe 64
    }

    // === Entity Validation Tests ===

    @Test
    fun `entity with different quality values`() {
        val qualities = listOf(1, 50, 80, 100)

        for (quality in qualities) {
            val entity = CompressionHistoryEntity(
                pathHash = "hash",
                path = "/test.jpg",
                originalSize = 1_000_000L,
                compressedSize = 500_000L,
                quality = quality,
                compressedAt = Instant.now(),
            )

            entity.quality shouldBe quality
        }
    }

    @Test
    fun `entity data class equality`() {
        val timestamp = Instant.now()

        val entity1 = CompressionHistoryEntity(
            pathHash = "abc123",
            path = "/test.jpg",
            originalSize = 1_000_000L,
            compressedSize = 500_000L,
            quality = 80,
            compressedAt = timestamp,
        )

        val entity2 = CompressionHistoryEntity(
            pathHash = "abc123",
            path = "/test.jpg",
            originalSize = 1_000_000L,
            compressedSize = 500_000L,
            quality = 80,
            compressedAt = timestamp,
        )

        entity1 shouldBe entity2
    }

    @Test
    fun `entity data class inequality - different path hash`() {
        val timestamp = Instant.now()

        val entity1 = CompressionHistoryEntity(
            pathHash = "abc123",
            path = "/test.jpg",
            originalSize = 1_000_000L,
            compressedSize = 500_000L,
            quality = 80,
            compressedAt = timestamp,
        )

        val entity2 = CompressionHistoryEntity(
            pathHash = "xyz789",
            path = "/test.jpg",
            originalSize = 1_000_000L,
            compressedSize = 500_000L,
            quality = 80,
            compressedAt = timestamp,
        )

        entity1 shouldNotBe entity2
    }

    @Test
    fun `compression ratio calculation from entity`() {
        val entity = CompressionHistoryEntity(
            pathHash = "hash",
            path = "/test.jpg",
            originalSize = 10_000_000L,
            compressedSize = 6_500_000L,
            quality = 80,
            compressedAt = Instant.now(),
        )

        val ratio = entity.compressedSize.toDouble() / entity.originalSize.toDouble()

        ratio shouldBe 0.65
    }

    @Test
    fun `entity handles large file sizes`() {
        val entity = CompressionHistoryEntity(
            pathHash = "hash",
            path = "/large_file.jpg",
            originalSize = 1_000_000_000L, // 1 GB
            compressedSize = 650_000_000L,
            quality = 80,
            compressedAt = Instant.now(),
        )

        val savings = entity.originalSize - entity.compressedSize
        savings shouldBe 350_000_000L // 350 MB saved
    }

    // === Simulated Database Operation Tests ===

    @Test
    fun `simulated compression record and lookup workflow`() {
        // Simulating what the database does
        val compressedPathHashes = mutableSetOf<String>()

        val path1 = "/storage/emulated/0/DCIM/Camera/IMG_001.jpg"
        val path2 = "/storage/emulated/0/DCIM/Camera/IMG_002.jpg"

        // Record compression
        compressedPathHashes.add(pathToHash(path1))

        // Check if compressed
        (pathToHash(path1) in compressedPathHashes) shouldBe true
        (pathToHash(path2) in compressedPathHashes) shouldBe false

        // Record another compression
        compressedPathHashes.add(pathToHash(path2))

        // Now both should be found
        (pathToHash(path1) in compressedPathHashes) shouldBe true
        (pathToHash(path2) in compressedPathHashes) shouldBe true
    }

    @Test
    fun `simulated clear operation`() {
        val compressedPathHashes = mutableSetOf<String>()

        // Add some hashes
        compressedPathHashes.add(pathToHash("/img1.jpg"))
        compressedPathHashes.add(pathToHash("/img2.jpg"))

        compressedPathHashes.size shouldBe 2

        // Clear
        compressedPathHashes.clear()

        compressedPathHashes.size shouldBe 0
    }

    @Test
    fun `hash is deterministic across multiple calls`() {
        val path = "/storage/emulated/0/DCIM/Camera/IMG_001.jpg"

        // Call multiple times
        val hashes = (1..10).map { pathToHash(path) }

        // All should be identical
        hashes.toSet().size shouldBe 1
    }
}
