package eu.darken.sdmse.widget.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import eu.darken.sdmse.common.ui.R as CommonUiR

/**
 * Colour selection for the widget's storage ring. The ring is drawn into a [android.graphics.Bitmap],
 * and Robolectric's Canvas is a stub that rasterises nothing — asserting pixels there would prove
 * nothing, so the pure selection function is what gets tested.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class)
class StorageRingColorTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [30])
    fun `normal storage below API 31 uses the app primary`() {
        storageArcColor(context, isLow = false) shouldBe context.getColor(CommonUiR.color.md_theme_primary)
    }

    @Test
    @Config(sdk = [33])
    fun `normal storage on API 31+ uses the Material You accent`() {
        storageArcColor(context, isLow = false) shouldBe context.getColor(android.R.color.system_accent1_500)
    }

    @Test
    @Config(sdk = [30])
    fun `low storage below API 31 is amber`() {
        storageArcColor(context, isLow = true) shouldBe context.getColor(CommonUiR.color.md_theme_storageLow)
    }

    @Test
    @Config(sdk = [33])
    fun `low storage wins over the Material You accent`() {
        // The whole point of ordering the low branch first: on a device whose wallpaper accent
        // happens to look like a warning, or like anything else, the warning must still win.
        storageArcColor(context, isLow = true) shouldBe context.getColor(CommonUiR.color.md_theme_storageLow)
        storageArcColor(context, isLow = true) shouldNotBe context.getColor(android.R.color.system_accent1_500)
    }
}
