package eu.darken.sdmse.exclusion.ui

import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionEditorOptions
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * A PathExclusionEditorRoute on the Navigation3 back stack is persisted through the androidx savedstate codec.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class PathExclusionEditorRouteSavedStateTest : BaseTest() {

    @Test
    fun `PathExclusionEditorRoute saved state round-trip`() {
        val original = PathExclusionEditorRoute(
            exclusionId = "exc-123",
            initial = PathExclusionEditorOptions(
                targetPath = LocalPath.build("/storage/emulated/0/DCIM"),
            ),
        )

        val savedState = encodeToSavedState(PathExclusionEditorRoute.serializer(), original)
        decodeFromSavedState(PathExclusionEditorRoute.serializer(), savedState) shouldBe original
    }
}
