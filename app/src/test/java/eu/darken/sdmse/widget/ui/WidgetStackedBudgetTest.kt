package eu.darken.sdmse.widget.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.widget.WidgetRenderState
import eu.darken.sdmse.widget.WidgetRenderState.Data.StorageEntry
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import testhelpers.BaseTest
import testhelpers.TestApplication
import eu.darken.sdmse.common.R as CommonR

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = TestApplication::class)
class WidgetStackedBudgetTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun measuredLineHeight(sizeSp: Float, typeface: Typeface): Dp {
        val view = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            this.typeface = typeface
            text = "Internal 124 GB / 128 GB"
        }
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(unspecified, unspecified)
        return (view.measuredHeight / context.resources.displayMetrics.density).dp
    }

    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    // The budget may overestimate (the stacked layout then hands over to the row layout a bit early),
    // but an underestimate brings the clipped button back.
    @Test
    fun `text line budgets cover the measured line heights`() {
        val measured = mapOf(
            "title" to measuredLineHeight(15f, bold),
            "freed" to measuredLineHeight(11f, Typeface.DEFAULT),
            "row label" to measuredLineHeight(12f, medium),
            "row value" to measuredLineHeight(13f, bold),
            "button label" to measuredLineHeight(13f, medium),
        )
        println("Measured widget text line heights: $measured")

        (StackedBudget.TITLE_LINE >= measured.getValue("title")) shouldBe true
        (StackedBudget.FREED_LINE >= measured.getValue("freed")) shouldBe true
        (StackedBudget.ROW_LINE >= measured.getValue("row label")) shouldBe true
        (StackedBudget.ROW_LINE >= measured.getValue("row value")) shouldBe true
        (StackedBudget.BUTTON_LINE >= measured.getValue("button label")) shouldBe true
    }

    @Test
    fun `a single storage fits from about 157dp`() {
        StackedBudget.rowsThatFit(height = 156.dp, rowCount = 1, fontScale = 1f) shouldBe 0
        StackedBudget.rowsThatFit(height = 157.dp, rowCount = 1, fontScale = 1f) shouldBe 1
        StackedBudget.rowsThatFit(height = 120.dp, rowCount = 1, fontScale = 1f) shouldBe 0
    }

    @Test
    fun `two storages at about 180dp keep only the primary row`() {
        StackedBudget.rowsThatFit(height = 180.dp, rowCount = 2, fontScale = 1f) shouldBe 1
        StackedBudget.rowsThatFit(height = 198.dp, rowCount = 2, fontScale = 1f) shouldBe 2
    }

    @Test
    fun `rows never exceed the available storages`() {
        StackedBudget.rowsThatFit(height = 360.dp, rowCount = 3, fontScale = 1f) shouldBe 3
        StackedBudget.rowsThatFit(height = 360.dp, rowCount = 1, fontScale = 1f) shouldBe 1
    }

    @Test
    fun `a larger font scale needs more height`() {
        StackedBudget.rowsThatFit(height = 198.dp, rowCount = 2, fontScale = 1.3f) shouldBe 1
        StackedBudget.rowsThatFit(height = 157.dp, rowCount = 1, fontScale = 1.3f) shouldBe 0
    }

    private val twoStoragesCancellable = WidgetRenderState.Data(
        storages = listOf(
            StorageEntry(StorageEntry.Kind.INTERNAL, usedBytes = 124_000_000_000L, totalBytes = 128_000_000_000L),
            StorageEntry(StorageEntry.Kind.EXTERNAL, usedBytes = 209_000_000_000L, totalBytes = 256_000_000_000L),
        ),
        freedBytes = 22_000_000_000L,
        isWorking = true,
        isCancellable = true,
    )

    @Test
    fun `two storages at 180dp drop the second row and keep the cancel button`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(220.dp, 180.dp))

        provideComposable { WidgetContent(twoStoragesCancellable) }

        onNode(hasTextEqualTo(context.getString(R.string.widget_home_storage_internal))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.widget_home_storage_external))).assertDoesNotExist()
        onNode(hasTextEqualTo(context.getString(CommonR.string.general_cancel_action))).assertExists()
    }

    @Test
    fun `two storages with enough height show both rows`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(220.dp, 200.dp))

        provideComposable { WidgetContent(twoStoragesCancellable) }

        onNode(hasTextEqualTo(context.getString(R.string.widget_home_storage_internal))).assertExists()
        onNode(hasTextEqualTo(context.getString(R.string.widget_home_storage_external))).assertExists()
        onNode(hasTextEqualTo(context.getString(CommonR.string.general_cancel_action))).assertExists()
    }
}
