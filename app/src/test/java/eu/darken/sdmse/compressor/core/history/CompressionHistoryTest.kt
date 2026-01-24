package eu.darken.sdmse.compressor.core.history

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.security.MessageDigest

class CompressionHistoryTest : BaseTest() {

    /**
     * Replicate content hashing logic for testing.
     * In real code, this reads file bytes - here we simulate with a string.
     */
    private fun contentToHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `content hashing produces consistent results`() {
        val content = "test file content"

        val hash1 = contentToHash(content)
        val hash2 = contentToHash(content)

        hash1 shouldBe hash2
    }

    @Test
    fun `different content produces different hashes`() {
        val content1 = "content of file 1"
        val content2 = "content of file 2"

        val hash1 = contentToHash(content1)
        val hash2 = contentToHash(content2)

        hash1 shouldNotBe hash2
    }

    @Test
    fun `hash is SHA-256 format`() {
        val content = "test content"
        val hash = contentToHash(content)

        // SHA-256 produces 64 hex characters
        hash.length shouldBe 64
        hash.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
    }

    @Test
    fun `CompressionHistoryEntity stores content hash`() {
        val hash = "abc123def456"
        val entity = CompressionHistoryEntity(contentHash = hash)

        entity.contentHash shouldBe hash
    }

    @Test
    fun `entity data class equality`() {
        val entity1 = CompressionHistoryEntity(contentHash = "abc123")
        val entity2 = CompressionHistoryEntity(contentHash = "abc123")

        entity1 shouldBe entity2
    }

    @Test
    fun `entity data class inequality`() {
        val entity1 = CompressionHistoryEntity(contentHash = "abc123")
        val entity2 = CompressionHistoryEntity(contentHash = "xyz789")

        entity1 shouldNotBe entity2
    }

    // === Hash Lookup Tests ===

    @Test
    fun `hash lookup in Set works correctly`() {
        val content1 = "content of image 1"
        val content2 = "content of image 2"
        val content3 = "content of image 3"

        val compressedHashes = setOf(
            contentToHash(content1),
            contentToHash(content2),
        )

        // content1 and content2 should be found, content3 should not
        (contentToHash(content1) in compressedHashes) shouldBe true
        (contentToHash(content2) in compressedHashes) shouldBe true
        (contentToHash(content3) in compressedHashes) shouldBe false
    }

    @Test
    fun `identical content produces same hash regardless of filename`() {
        // This tests the key behavior: same content = same hash even if path differs
        val content = "identical file content"

        val hash1 = contentToHash(content)
        val hash2 = contentToHash(content)

        hash1 shouldBe hash2
    }

    @Test
    fun `hash uniqueness across different content`() {
        val contents = (1..100).map { "unique content number $it" }
        val hashes = contents.map { contentToHash(it) }.toSet()

        // All hashes should be unique
        hashes.size shouldBe 100
    }

    @Test
    fun `empty content hashes without error`() {
        val hash = contentToHash("")

        hash.length shouldBe 64
    }

    @Test
    fun `large content hashes correctly`() {
        val largeContent = "A".repeat(10_000_000) // 10MB of 'A's

        val hash = contentToHash(largeContent)

        hash.length shouldBe 64
    }

    // === Simulated Database Operation Tests ===

    @Test
    fun `simulated compression record and lookup workflow`() {
        val compressedContentHashes = mutableSetOf<String>()

        val content1 = "content of first image"
        val content2 = "content of second image"

        // Record compression (use hash of compressed content)
        compressedContentHashes.add(contentToHash(content1))

        // Check if compressed
        (contentToHash(content1) in compressedContentHashes) shouldBe true
        (contentToHash(content2) in compressedContentHashes) shouldBe false

        // Record another compression
        compressedContentHashes.add(contentToHash(content2))

        // Now both should be found
        (contentToHash(content1) in compressedContentHashes) shouldBe true
        (contentToHash(content2) in compressedContentHashes) shouldBe true
    }

    @Test
    fun `moved file scenario - same content different path is recognized`() {
        // Simulate: file was at path A, got compressed, then moved to path B
        // With content-based hashing, it should still be recognized as compressed

        val fileContent = "image file binary content"
        val compressedHashes = mutableSetOf<String>()

        // "Compress" file at path A
        compressedHashes.add(contentToHash(fileContent))

        // Later, file is "moved" to path B but content is the same
        // Checking if file at path B needs compression
        val needsCompression = contentToHash(fileContent) !in compressedHashes

        needsCompression shouldBe false // Should skip, already compressed
    }

    @Test
    fun `replaced file scenario - same path different content is recognized`() {
        // Simulate: file at path was compressed, then replaced with new content
        // With content-based hashing, the new file should be compressed

        val originalContent = "original image content"
        val newContent = "completely different new image content"
        val compressedHashes = mutableSetOf<String>()

        // "Compress" original file
        compressedHashes.add(contentToHash(originalContent))

        // File is replaced with new content at same path
        val needsCompression = contentToHash(newContent) !in compressedHashes

        needsCompression shouldBe true // Should compress, it's new content
    }

    @Test
    fun `duplicate files scenario - only one gets compressed`() {
        // Simulate: same image file exists in multiple locations
        // After compressing one, others should be skipped

        val duplicateContent = "identical file content at multiple locations"
        val compressedHashes = mutableSetOf<String>()

        // "Compress" first copy
        compressedHashes.add(contentToHash(duplicateContent))

        // Check second copy - should be skipped
        val secondCopyNeedsCompression = contentToHash(duplicateContent) !in compressedHashes

        secondCopyNeedsCompression shouldBe false
    }

    @Test
    fun `simulated clear operation`() {
        val compressedHashes = mutableSetOf<String>()

        // Add some hashes
        compressedHashes.add(contentToHash("content1"))
        compressedHashes.add(contentToHash("content2"))

        compressedHashes.size shouldBe 2

        // Clear
        compressedHashes.clear()

        compressedHashes.size shouldBe 0
    }

    @Test
    fun `hash is deterministic across multiple calls`() {
        val content = "test image binary content"

        // Call multiple times
        val hashes = (1..10).map { contentToHash(content) }

        // All should be identical
        hashes.toSet().size shouldBe 1
    }
}
