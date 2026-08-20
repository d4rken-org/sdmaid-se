package eu.darken.sdmse.main.core.shortcuts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * The clean shortcuts round-trip through an intent extra that an exported activity reads back.
 * Both halves are pinned here: what [AppShortcut.ToolAction] writes, and what
 * [resolveCleanShortcutTool] accepts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ToolShortcutMappingTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the shortcut id is stable per tool`() {
        OneTapCleaner.ONECLICK_TYPES.map { AppShortcut.ToolAction(it).id } shouldBe listOf(
            "clean_corpsefinder",
            "clean_systemcleaner",
            "clean_appcleaner",
            "clean_deduplicator",
        )
    }

    @Test
    fun `the intent carries the action and the tool extra, and resolves back`() {
        OneTapCleaner.ONECLICK_TYPES.forEach { type ->
            val intent = AppShortcut.ToolAction(type).createIntent(context)

            intent.action shouldBe ShortcutActivity.ACTION_CLEAN_TOOL
            intent.component?.className shouldBe ShortcutActivity::class.java.name
            // Guards the Intent.type shadowing trap: writing the extra inside the apply block would
            // set the MIME type instead and leave the extra absent.
            intent.type shouldBe null
            intent.getStringExtra(ShortcutActivity.EXTRA_TOOL) shouldBe type.name

            resolveCleanShortcutTool(intent.getStringExtra(ShortcutActivity.EXTRA_TOOL)) shouldBe type
        }
    }

    @Test
    fun `resolving is lenient about anything an external caller can send`() {
        // The trampoline is exported, so the extra can be absent or garbage. valueOf would throw.
        resolveCleanShortcutTool(null) shouldBe null
        resolveCleanShortcutTool("") shouldBe null
        resolveCleanShortcutTool("corpsefinder") shouldBe null
        resolveCleanShortcutTool("../../etc/passwd") shouldBe null
        // A real tool that has no clean shortcut must not resolve either.
        resolveCleanShortcutTool(SDMTool.Type.ANALYZER.name) shouldBe null
        resolveCleanShortcutTool(SDMTool.Type.APPCONTROL.name) shouldBe null
    }

    @Test
    fun `a tool without a one-click task cannot become a shortcut`() {
        shouldThrow<IllegalArgumentException> { AppShortcut.ToolAction(SDMTool.Type.ANALYZER) }
    }

    @Test
    fun `every clean shortcut has its own icon and labels`() {
        val icons = OneTapCleaner.ONECLICK_TYPES.map { it.cleanShortcutIconRes }
        icons.distinct().size shouldBe icons.size

        val shortLabels = OneTapCleaner.ONECLICK_TYPES.map { context.getString(it.cleanShortcutShortLabelRes) }
        shortLabels.distinct().size shouldBe shortLabels.size
        shortLabels.forEach { it shouldNotBe "" }

        val longLabels = OneTapCleaner.ONECLICK_TYPES.map { context.getString(it.cleanShortcutLongLabelRes) }
        longLabels.distinct().size shouldBe longLabels.size
    }

    @Test
    fun `toShortcutInfo carries the rank it was published with`() {
        val info = AppShortcut.ToolAction(SDMTool.Type.APPCLEANER).toShortcutInfo(context, rank = 3)
        info.id shouldBe "clean_appcleaner"
        info.rank shouldBe 3
    }
}
