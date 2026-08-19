package eu.darken.sdmse.main.core.shortcuts

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.ui.AppCleanerListRoute
import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.navigation.routes.AppControlListRoute
import eu.darken.sdmse.common.navigation.routes.DeviceStorageRoute
import eu.darken.sdmse.common.navigation.routes.SwiperSessionsRoute
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderListRoute
import eu.darken.sdmse.deduplicator.ui.DeduplicatorListRoute
import eu.darken.sdmse.main.core.DashboardCardType
import eu.darken.sdmse.main.core.shortcutIconRes
import eu.darken.sdmse.main.core.shortcutRoute
import eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity
import eu.darken.sdmse.scheduler.ui.SchedulerManagerRoute
import eu.darken.sdmse.squeezer.ui.SqueezerSetupRoute
import eu.darken.sdmse.stats.ui.ReportsRoute
import eu.darken.sdmse.systemcleaner.ui.SystemCleanerListRoute
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * Exact expected tables for the per-tool shortcut mapping. Asserting "non-zero" or "not null" would
 * happily pass a copy-paste swap between two tools, which is the realistic failure mode for a pair
 * of ten-branch `when` expressions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ShortcutToolMappingTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val expectedRoutes: Map<DashboardCardType, NavigationDestination> = mapOf(
        DashboardCardType.CORPSEFINDER to CorpseFinderListRoute,
        DashboardCardType.SYSTEMCLEANER to SystemCleanerListRoute,
        DashboardCardType.APPCLEANER to AppCleanerListRoute,
        DashboardCardType.DEDUPLICATOR to DeduplicatorListRoute,
        DashboardCardType.APPCONTROL to AppControlListRoute,
        DashboardCardType.SWIPER to SwiperSessionsRoute,
        DashboardCardType.SQUEEZER to SqueezerSetupRoute,
        DashboardCardType.ANALYZER to DeviceStorageRoute,
        DashboardCardType.SCHEDULER to SchedulerManagerRoute,
        DashboardCardType.STATS to ReportsRoute,
    )

    private val expectedIcons: Map<DashboardCardType, Int> = mapOf(
        DashboardCardType.CORPSEFINDER to R.drawable.ic_shortcut_corpsefinder,
        DashboardCardType.SYSTEMCLEANER to R.drawable.ic_shortcut_systemcleaner,
        DashboardCardType.APPCLEANER to R.drawable.ic_shortcut_appcleaner,
        DashboardCardType.DEDUPLICATOR to R.drawable.ic_shortcut_deduplicator,
        // Unchanged on purpose: users may have this icon on a pinned home-screen shortcut.
        DashboardCardType.APPCONTROL to R.drawable.ic_shortcut_apps,
        DashboardCardType.SWIPER to R.drawable.ic_shortcut_swiper,
        DashboardCardType.SQUEEZER to R.drawable.ic_shortcut_squeezer,
        DashboardCardType.ANALYZER to R.drawable.ic_shortcut_analyzer,
        DashboardCardType.SCHEDULER to R.drawable.ic_shortcut_scheduler,
        DashboardCardType.STATS to R.drawable.ic_shortcut_stats,
    )

    @Test
    fun `every tool maps to its expected route`() {
        expectedRoutes.keys shouldBe DashboardCardType.entries.toSet()
        DashboardCardType.entries.forEach { type ->
            type.shortcutRoute shouldBe expectedRoutes.getValue(type)
        }
    }

    @Test
    fun `every tool maps to its expected icon`() {
        expectedIcons.keys shouldBe DashboardCardType.entries.toSet()
        DashboardCardType.entries.forEach { type ->
            type.shortcutIconRes shouldBe expectedIcons.getValue(type)
        }
    }

    @Test
    fun `shortcut ids and icons are distinct per tool`() {
        val ids = DashboardCardType.entries.map { AppShortcut.Tool(it).id }
        ids.distinct().size shouldBe DashboardCardType.entries.size
        ids.contains(AppShortcut.MainAction.OneTap.id) shouldBe false

        DashboardCardType.entries.map { it.shortcutIconRes }
            .distinct().size shouldBe DashboardCardType.entries.size
    }

    @Test
    fun `AppControl keeps its historic shortcut id so upgrades update it in place`() {
        AppShortcut.Tool(DashboardCardType.APPCONTROL).id shouldBe "appcontrol"
    }

    @Test
    fun `every tool intent targets the trampoline with the open-tool action and its own extra`() {
        DashboardCardType.entries.forEach { type ->
            val intent = AppShortcut.Tool(type).createIntent(context)

            intent.component?.className shouldBe ShortcutActivity::class.java.name
            intent.action shouldBe ShortcutActivity.ACTION_OPEN_TOOL
            intent.getStringExtra(ShortcutActivity.EXTRA_TOOL) shouldBe type.name
            intent.flags shouldBe (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    @Test
    fun `the tool extra round-trips back to its type`() {
        DashboardCardType.entries.forEach { type ->
            val intent = AppShortcut.Tool(type).createIntent(context)
            resolveShortcutTool(intent.getStringExtra(ShortcutActivity.EXTRA_TOOL)) shouldBe type
        }
    }
}
