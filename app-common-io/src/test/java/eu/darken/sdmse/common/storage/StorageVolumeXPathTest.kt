package eu.darken.sdmse.common.storage

import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.File

/** At API 28 both `getPath()` and the `directory` fallback go through reflection, so plain objects stand in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestApplication::class)
class StorageVolumeXPathTest : BaseTest() {

    class VolumeWithoutGetPath {
        fun getPathFile() = File("/storage/1234-5678")
    }

    class VolumeWithGetPath {
        fun getPath() = "/storage/emulated/0"
        fun getPathFile() = File("/somewhere/else")
    }

    @Test fun `path comes from getPath when it is reachable`() {
        StorageVolumeX(VolumeWithGetPath()).path shouldBe "/storage/emulated/0"
    }

    @Test fun `path falls back to the volume directory when getPath is unreachable`() {
        StorageVolumeX(VolumeWithoutGetPath()).path shouldBe "/storage/1234-5678"
    }
}
