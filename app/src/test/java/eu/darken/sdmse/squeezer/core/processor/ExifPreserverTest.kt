package eu.darken.sdmse.squeezer.core.processor

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ExifPreserverTest : BaseTest() {

    private lateinit var testDir: File
    private lateinit var exifPreserver: ExifPreserver

    @Before
    fun setup() {
        testDir = File(IO_TEST_BASEDIR, "exif_preserver_test")
        testDir.mkdirs()
        exifPreserver = ExifPreserver()
    }

    @After
    fun cleanup() {
        testDir.deleteRecursively()
    }

    private fun createTestJpeg(file: File) {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
    }

    // === Compression Marker Tests ===

    @Test
    fun `hasCompressionMarker returns false for file without marker`() {
        val file = File(testDir, "no_marker.jpg")
        createTestJpeg(file)

        exifPreserver.hasCompressionMarker(file) shouldBe false
    }

    @Test
    fun `hasCompressionMarker returns true after writeCompressionMarker`() {
        val file = File(testDir, "with_marker.jpg")
        createTestJpeg(file)

        exifPreserver.hasCompressionMarker(file) shouldBe false

        exifPreserver.writeCompressionMarker(file.absolutePath)

        exifPreserver.hasCompressionMarker(file) shouldBe true
    }

    @Test
    fun `hasCompressionMarker returns false for non-existent file`() {
        val file = File(testDir, "does_not_exist.jpg")

        exifPreserver.hasCompressionMarker(file) shouldBe false
    }

    @Test
    fun `hasCompressionMarker returns false for corrupt file`() {
        val file = File(testDir, "corrupt.jpg")
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))

        exifPreserver.hasCompressionMarker(file) shouldBe false
    }

    @Test
    fun `writeCompressionMarker writes correct format`() {
        val file = File(testDir, "marker_format.jpg")
        createTestJpeg(file)

        exifPreserver.writeCompressionMarker(file.absolutePath)

        val exif = ExifInterface(file)
        val userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
        userComment shouldBe "SDMSE:v1"
    }

    @Test
    fun `marker survives file copy`() {
        val original = File(testDir, "original.jpg")
        val copy = File(testDir, "copy.jpg")
        createTestJpeg(original)

        exifPreserver.writeCompressionMarker(original.absolutePath)
        original.copyTo(copy)

        exifPreserver.hasCompressionMarker(copy) shouldBe true
    }

    @Test
    fun `writeCompressionMarker does not crash on non-existent file`() {
        val file = File(testDir, "does_not_exist.jpg")

        // Should not throw, just log a warning
        exifPreserver.writeCompressionMarker(file.absolutePath)
    }

    @Test
    fun `writeCompressionMarker does not crash on corrupt file`() {
        val file = File(testDir, "corrupt.jpg")
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))

        // Should not throw, just log a warning
        exifPreserver.writeCompressionMarker(file.absolutePath)
    }

    // === EXIF Extraction Tests ===

    @Test
    fun `extractExif returns ExifData for minimal JPEG`() {
        // A minimal JPEG created via Bitmap.compress() may have basic EXIF like dimensions
        val file = File(testDir, "minimal.jpg")
        createTestJpeg(file)

        val exifData = exifPreserver.extractExif(file)

        // May or may not have data depending on what Bitmap.compress() writes
        // The important thing is it doesn't crash
        // If data exists, it should contain valid attributes
        if (exifData != null) {
            exifData.attributes.isNotEmpty() shouldBe true
        }
    }

    @Test
    fun `extractExif returns data for file with EXIF`() {
        val file = File(testDir, "with_exif.jpg")
        createTestJpeg(file)

        // Add some EXIF data
        val exif = ExifInterface(file)
        exif.setAttribute(ExifInterface.TAG_MAKE, "TestCamera")
        exif.setAttribute(ExifInterface.TAG_MODEL, "TestModel")
        exif.saveAttributes()

        val exifData = exifPreserver.extractExif(file)

        exifData.shouldNotBeNull()
        exifData.attributes[ExifInterface.TAG_MAKE] shouldBe "TestCamera"
        exifData.attributes[ExifInterface.TAG_MODEL] shouldBe "TestModel"
    }

    @Test
    fun `extractExif returns null for non-existent file`() {
        val file = File(testDir, "does_not_exist.jpg")

        val exifData = exifPreserver.extractExif(file)

        exifData.shouldBeNull()
    }

    // === EXIF Apply Tests ===

    @Test
    fun `applyExif restores preserved attributes`() {
        val original = File(testDir, "original_exif.jpg")
        val target = File(testDir, "target_exif.jpg")
        createTestJpeg(original)
        createTestJpeg(target)

        // Set EXIF on original
        val exif = ExifInterface(original)
        exif.setAttribute(ExifInterface.TAG_MAKE, "OriginalCamera")
        exif.setAttribute(ExifInterface.TAG_DATETIME, "2024:01:15 10:30:00")
        exif.saveAttributes()

        // Extract from original
        val exifData = exifPreserver.extractExif(original)
        exifData.shouldNotBeNull()

        // Apply to target
        exifPreserver.applyExif(target.absolutePath, exifData)

        // Verify target has the EXIF
        val targetExif = ExifInterface(target)
        targetExif.getAttribute(ExifInterface.TAG_MAKE) shouldBe "OriginalCamera"
        targetExif.getAttribute(ExifInterface.TAG_DATETIME) shouldBe "2024:01:15 10:30:00"
    }

    @Test
    fun `applyExif handles null exifData gracefully`() {
        val file = File(testDir, "target.jpg")
        createTestJpeg(file)

        // Should not throw
        exifPreserver.applyExif(file.absolutePath, null)
    }

    @Test
    fun `applyExif does not crash on non-existent target`() {
        val exifData = ExifPreserver.ExifData(
            attributes = mapOf(ExifInterface.TAG_MAKE to "TestCamera")
        )

        // Should not throw, just log a warning
        exifPreserver.applyExif("/non/existent/path.jpg", exifData)
    }

    // === Combined Tests ===

    @Test
    fun `marker and EXIF data can coexist`() {
        val file = File(testDir, "combined.jpg")
        createTestJpeg(file)

        // Add EXIF data
        val exif = ExifInterface(file)
        exif.setAttribute(ExifInterface.TAG_MAKE, "TestCamera")
        exif.saveAttributes()

        // Add marker
        exifPreserver.writeCompressionMarker(file.absolutePath)

        // Both should be readable
        exifPreserver.hasCompressionMarker(file) shouldBe true

        val readExif = ExifInterface(file)
        readExif.getAttribute(ExifInterface.TAG_MAKE) shouldBe "TestCamera"
        readExif.getAttribute(ExifInterface.TAG_USER_COMMENT) shouldBe "SDMSE:v1"
    }

    @Test
    fun `writeCompressionMarker appends to existing user comment`() {
        val file = File(testDir, "with_comment.jpg")
        createTestJpeg(file)

        // Add existing user comment
        val exif = ExifInterface(file)
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "My photo notes")
        exif.saveAttributes()

        // Write marker
        exifPreserver.writeCompressionMarker(file.absolutePath)

        // Marker should be appended, not replace the original comment
        val readExif = ExifInterface(file)
        val userComment = readExif.getAttribute(ExifInterface.TAG_USER_COMMENT)
        userComment shouldBe "My photo notes SDMSE:v1"

        // Marker should still be detected
        exifPreserver.hasCompressionMarker(file) shouldBe true
    }

    @Test
    fun `extractExif preserves user comment`() {
        val original = File(testDir, "original_comment.jpg")
        val target = File(testDir, "target_comment.jpg")
        createTestJpeg(original)
        createTestJpeg(target)

        // Set user comment on original
        val exif = ExifInterface(original)
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "Important notes")
        exif.saveAttributes()

        // Extract and apply
        val exifData = exifPreserver.extractExif(original)
        exifData.shouldNotBeNull()
        exifPreserver.applyExif(target.absolutePath, exifData)

        // User comment should be preserved
        val targetExif = ExifInterface(target)
        targetExif.getAttribute(ExifInterface.TAG_USER_COMMENT) shouldBe "Important notes"
    }
}
