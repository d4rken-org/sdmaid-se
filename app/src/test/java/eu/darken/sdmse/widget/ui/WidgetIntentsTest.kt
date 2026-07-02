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
        val intents = listOf(
            widgetOpenAppIntent(context),
            widgetOpenAnalyzerIntent(context),
            AppShortcut.MainAction.OneTap.createIntent(context),
            widgetCancelIntent(context),
        )

        intents.forEachIndexed { i, a ->
            intents.forEachIndexed { j, b ->
                if (i != j) a.filterEquals(b) shouldBe false
            }
        }
    }

    @Test
    fun `open-app reuses a live MainActivity instead of stacking a duplicate`() {
        // The data URI defeats the system's root-intent matching, so without these flags a
        // backgrounded app gets a second dashboard instance — and backing out of it froze all
        // navigation (singleton nav controller wired to the finished instance's back stack).
        val flags = widgetOpenAppIntent(context).flags
        (flags and android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP) shouldBe android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        (flags and android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP) shouldBe android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    @Test
    fun `cancel targets ShortcutActivity with the one-click cancel action`() {
        val cancel = widgetCancelIntent(context)
        cancel.component?.className shouldBe ShortcutActivity::class.java.name
        cancel.action shouldBe ShortcutActivity.ACTION_CANCEL_ONECLICK
    }

    @Test
    fun `open-analyzer carries the analyzer shortcut action, open-app carries none`() {
        widgetOpenAnalyzerIntent(context)
            .getStringExtra(ShortcutActivity.EXTRA_SHORTCUT_ACTION) shouldBe ShortcutActivity.ACTION_OPEN_ANALYZER
        widgetOpenAppIntent(context)
            .getStringExtra(ShortcutActivity.EXTRA_SHORTCUT_ACTION) shouldBe null
    }
}
