package eu.darken.sdmse.widget.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.main.core.shortcuts.AppShortcut
import eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * Guards the widget's three tap targets against PendingIntent collapse. `Intent.filterEquals` ignores
 * extras and flags, so intents that differ only there share one PendingIntent — which previously made
 * a storage tap open the dashboard instead of the Analyzer. The tap intents must stay mutually
 * distinct by component/action/data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class WidgetIntentsTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `widget tap intents are all filterEquals-distinct so their PendingIntents don't collapse`() {
        val app = widgetOpenAppIntent(context)
        val analyzer = widgetOpenAnalyzerIntent(context)
        val clean = AppShortcut.MainAction.OneTap.createIntent(context)

        app.filterEquals(analyzer) shouldBe false
        app.filterEquals(clean) shouldBe false
        analyzer.filterEquals(clean) shouldBe false
    }

    @Test
    fun `open-analyzer carries the analyzer shortcut action, open-app carries none`() {
        widgetOpenAnalyzerIntent(context)
            .getStringExtra(ShortcutActivity.EXTRA_SHORTCUT_ACTION) shouldBe ShortcutActivity.ACTION_OPEN_ANALYZER
        widgetOpenAppIntent(context)
            .getStringExtra(ShortcutActivity.EXTRA_SHORTCUT_ACTION) shouldBe null
    }
}
