package eu.darken.sdmse.main.core.shortcuts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.main.core.DashboardCardType
import eu.darken.sdmse.main.core.shortcutIconRes
import io.kotest.matchers.shouldBe
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * The shortcut icons are a mechanical translation of Compose vector sources into VectorDrawable XML.
 * A mistyped coordinate or a dropped command survives compilation and only shows up as a broken
 * glyph in the launcher menu, so every icon is inflated and actually drawn here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutIconRenderTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    /** Every `ic_shortcut_*` drawable in the app module, by reflection, so new ones are covered. */
    private fun shortcutIcons(): Map<String, Int> = R.drawable::class.java.fields
        .filter { it.name.startsWith("ic_shortcut_") }
        .associate { it.name to it.getInt(null) }

    private fun coveredPixels(iconRes: Int): Int {
        val drawable = requireNotNull(context.getDrawable(iconRes)) { "No drawable for $iconRes" }
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))

        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        return pixels.count { it != 0 }
    }

    @Test
    fun `every shortcut icon inflates and draws something`() {
        val icons = shortcutIcons()
        icons.isNotEmpty() shouldBe true

        icons.forEach { (name, res) ->
            assertTrue("$name rendered blank", coveredPixels(res) > 0)
        }
    }

    @Test
    fun `every tool has one of the shortcut drawables and it renders`() {
        val known = shortcutIcons().values.toSet()
        DashboardCardType.entries.forEach { type ->
            assertTrue("$type has no ic_shortcut_* icon", known.contains(type.shortcutIconRes))
            assertTrue("$type icon rendered blank", coveredPixels(type.shortcutIconRes) > 0)
        }
    }
}
