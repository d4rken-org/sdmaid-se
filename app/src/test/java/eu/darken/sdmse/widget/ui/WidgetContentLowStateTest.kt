package eu.darken.sdmse.widget.ui

import android.content.Context
import androidx.glance.EmittableWithText
import androidx.glance.appwidget.EmittableLinearProgressIndicator
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.glance.unit.ColorProvider
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.widget.WidgetRenderState
import eu.darken.sdmse.widget.WidgetRenderState.Data.StorageEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * Guards the low-storage signal in the two bar-based widget layouts.
 *
 * The label assertions are the important ones: Glance 1.2.0-rc01 only applies a custom
 * [androidx.glance.appwidget.LinearProgressIndicator] tint on API 31+ and silently drops it below
 * that, so on Android 8-11 (minSdk is 26) the amber TEXT LABEL is the only low-storage signal the
 * widget can render. If a refactor drops the label colouring as "redundant with the bar", those
 * versions lose the warning entirely and nothing else in the suite notices.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class WidgetContentLowStateTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun entry(isLow: Boolean) = StorageEntry(
        kind = StorageEntry.Kind.INTERNAL,
        usedBytes = 126_000_000_000L,
        totalBytes = 128_000_000_000L,
        isLow = isLow,
    )

    private fun data(isLow: Boolean) = WidgetRenderState.Data(
        storages = listOf(entry(isLow)),
        freedBytes = 3_000_000_000L,
    )

    // Glance ships no colour matcher, so these reach into the emitted node. The Emittable types are
    // @RestrictTo(LIBRARY_GROUP) — public bytecode, lint-only, and lint doesn't scan unit-test
    // sources here.
    private fun hasTextColor(expected: ColorProvider) = GlanceNodeMatcher<MappedNode>(
        description = "text colour is $expected",
    ) { node ->
        val emittable = node.value.emittable
        emittable is EmittableWithText && emittable.style?.color == expected
    }

    private fun isProgressBarWithColor(expected: ColorProvider) = GlanceNodeMatcher<MappedNode>(
        description = "linear progress indicator with colour $expected",
    ) { node ->
        val emittable = node.value.emittable
        emittable is EmittableLinearProgressIndicator && emittable.color == expected
    }

    @Test
    fun `stacked layout - a low volume colours the storage label amber`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        provideComposable { StackedLayout(data(isLow = true)) }

        onNode(
            hasTextEqualTo(usedOfTotal(context, entry(isLow = true)))
                and hasTextColor(lowStorageColorProvider(context))
        ).assertExists()
    }

    @Test
    fun `stacked layout - a normal volume leaves the storage label alone`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        provideComposable { StackedLayout(data(isLow = false)) }

        // Positive first, so a changed label format can't make the negative assertion vacuous.
        onNode(hasTextEqualTo(usedOfTotal(context, entry(isLow = false)))).assertExists()
        onNode(
            hasTextEqualTo(usedOfTotal(context, entry(isLow = false)))
                and hasTextColor(lowStorageColorProvider(context))
        ).assertDoesNotExist()
    }

    @Test
    fun `value row layout - a low volume colours the storage label amber`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        provideComposable {
            ValueRowLayout(data(isLow = true), showButtonLabel = true, showFreedText = true)
        }

        onNode(
            hasTextEqualTo(usedOfTotal(context, entry(isLow = true)))
                and hasTextColor(lowStorageColorProvider(context))
        ).assertExists()
    }

    @Test
    fun `value row layout - a normal volume leaves the storage label alone`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        provideComposable {
            ValueRowLayout(data(isLow = false), showButtonLabel = true, showFreedText = true)
        }

        // Positive first, so a changed label format can't make the negative assertion vacuous.
        onNode(hasTextEqualTo(usedOfTotal(context, entry(isLow = false)))).assertExists()
        onNode(
            hasTextEqualTo(usedOfTotal(context, entry(isLow = false)))
                and hasTextColor(lowStorageColorProvider(context))
        ).assertDoesNotExist()
    }

    @Test
    fun `stacked layout - a low volume also colours the bar`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        provideComposable { StackedLayout(data(isLow = true)) }

        onNode(isProgressBarWithColor(lowStorageColorProvider(context))).assertExists()
    }

    @Test
    fun `value row layout - a low volume also colours the bar`() = runGlanceAppWidgetUnitTest {
        setContext(context)

        provideComposable {
            ValueRowLayout(data(isLow = true), showButtonLabel = true, showFreedText = true)
        }

        onNode(isProgressBarWithColor(lowStorageColorProvider(context))).assertExists()
    }
}
