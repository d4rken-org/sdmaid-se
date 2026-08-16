package eu.darken.sdmse.common.serialization

import android.os.Bundle
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * The Navigation3 back stack is persisted through the androidx savedstate codec, not JSON.
 * [APathSerializer] must therefore survive a round-trip through a non-JSON encoder/decoder pair.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class APathSerializerSavedStateTest : BaseTest() {

    private val treeUri = "content://com.android.externalstorage.documents/tree/primary%3A"

    private val localPath = LocalPath.build("/storage/emulated/0/DCIM")
    private val safPath = SAFPath(treeUri, listOf("Pictures", "Wallpaper"))
    private val rawPath = RawPath.build("/some/raw/path")

    @Test
    fun `LocalPath saved state round-trip`() {
        val original: APath = localPath
        decodeFromSavedState(APathSerializer, encodeToSavedState(APathSerializer, original)) shouldBe original
    }

    @Test
    fun `SAFPath saved state round-trip`() {
        val original: APath = safPath
        decodeFromSavedState(APathSerializer, encodeToSavedState(APathSerializer, original)) shouldBe original
    }

    @Test
    fun `RawPath saved state round-trip`() {
        val original: APath = rawPath
        decodeFromSavedState(APathSerializer, encodeToSavedState(APathSerializer, original)) shouldBe original
    }

    @Test
    fun `LocalPath saved state written by the previous release still decodes`() {
        val legacy = encodeToSavedState(LocalPath.serializer(), localPath)
        decodeFromSavedState(APathSerializer, legacy) shouldBe localPath
    }

    @Test
    fun `SAFPath saved state written by the previous release still decodes`() {
        val legacy = encodeToSavedState(SAFPath.serializer(), safPath)
        decodeFromSavedState(APathSerializer, legacy) shouldBe safPath
    }

    @Test
    fun `RawPath saved state written by the previous release still decodes`() {
        val legacy = encodeToSavedState(RawPath.serializer(), rawPath)
        decodeFromSavedState(APathSerializer, legacy) shouldBe rawPath
    }

    @Test
    fun `fields from multiple variants are rejected`() {
        val mixed = Bundle().apply {
            putString("file", "/a")
            putString("path", "/b")
        }
        shouldThrow<SerializationException> { decodeFromSavedState(APathSerializer, mixed) }
    }

    @Test
    fun `SAFPath without segments is rejected`() {
        val incomplete = Bundle().apply {
            putString("treeRoot", treeUri)
        }
        shouldThrow<SerializationException> { decodeFromSavedState(APathSerializer, incomplete) }
    }

    @Test
    fun `empty saved state is rejected`() {
        shouldThrow<SerializationException> { decodeFromSavedState(APathSerializer, Bundle()) }
    }
}
