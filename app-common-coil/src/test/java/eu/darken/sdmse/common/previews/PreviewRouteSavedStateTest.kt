package eu.darken.sdmse.common.previews

import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import eu.darken.sdmse.common.files.local.LocalPath
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * A PreviewRoute on the Navigation3 back stack is persisted through the androidx savedstate codec.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class PreviewRouteSavedStateTest : BaseTest() {

    @Test
    fun `PreviewRoute saved state round-trip`() {
        val original = PreviewRoute(
            options = PreviewOptions(
                paths = listOf(
                    LocalPath.build("/test/image.jpg"),
                    LocalPath.build("/test/photo.png"),
                ),
                position = 1,
            ),
        )

        val savedState = encodeToSavedState(PreviewRoute.serializer(), original)
        decodeFromSavedState(PreviewRoute.serializer(), savedState) shouldBe original
    }
}
