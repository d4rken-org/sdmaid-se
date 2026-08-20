package eu.darken.sdmse.main.core.shortcuts

import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.main.core.GeneralSettings
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.runTest2
import java.util.Locale
import android.content.pm.ShortcutManager as AndroidShortcutManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ShortcutManagerTest : BaseTest() {

    /** Records every publish attempt, so "did it republish?" is observable and not inferred. */
    private class FakeSystemShortcuts(var maxCount: Int = 15, var accept: Boolean = true) {
        val publishes = mutableListOf<List<ShortcutInfo>>()
        var removeAllCalls = 0

        val mock: AndroidShortcutManager = mockk<AndroidShortcutManager>(relaxed = true).apply {
            every { maxShortcutCountPerActivity } answers { maxCount }
            every { setDynamicShortcuts(any()) } answers {
                publishes.add(firstArg())
                accept
            }
            every { removeAllDynamicShortcuts() } answers { removeAllCalls++ }
        }
    }

    /**
     * Also records the [ComponentCallbacks] the manager registers, so a configuration change can be
     * delivered deterministically instead of hoping the framework dispatches one.
     */
    private class TestContext(
        base: Context,
        val shortcuts: FakeSystemShortcuts,
    ) : ContextWrapper(base) {
        val callbacks = mutableListOf<ComponentCallbacks>()

        override fun registerComponentCallbacks(callback: ComponentCallbacks) {
            callbacks.add(callback)
        }

        override fun unregisterComponentCallbacks(callback: ComponentCallbacks) {
            callbacks.remove(callback)
        }

        // Context.getSystemService(Class) is final and routes through the name-based overload.
        override fun getSystemService(name: String): Any? =
            if (name == Context.SHORTCUT_SERVICE) shortcuts.mock else super.getSystemService(name)

        fun changeConfiguration(config: Configuration) {
            callbacks.toList().forEach { it.onConfigurationChanged(config) }
        }
    }

    private val oneTapFlow = MutableStateFlow(false)
    private val corpseFinderFlow = MutableStateFlow(false)
    private val systemCleanerFlow = MutableStateFlow(false)
    private val appCleanerFlow = MutableStateFlow(false)
    private val deduplicatorFlow = MutableStateFlow(false)

    private fun setting(flow: MutableStateFlow<Boolean>): DataStoreValue<Boolean> =
        mockk<DataStoreValue<Boolean>>(relaxed = true).apply { every { this@apply.flow } returns flow }

    private val generalSettings = mockk<GeneralSettings>(relaxed = true).apply {
        every { shortcutOneClickEnabled } returns setting(oneTapFlow)
        every { shortcutCleanCorpseFinderEnabled } returns setting(corpseFinderFlow)
        every { shortcutCleanSystemCleanerEnabled } returns setting(systemCleanerFlow)
        every { shortcutCleanAppCleanerEnabled } returns setting(appCleanerFlow)
        every { shortcutCleanDeduplicatorEnabled } returns setting(deduplicatorFlow)
    }

    private fun CoroutineScope.start(shortcuts: FakeSystemShortcuts = FakeSystemShortcuts()): TestContext {
        val context = TestContext(ApplicationProvider.getApplicationContext(), shortcuts)
        ShortcutManager(
            context = context,
            appScope = this,
            generalSettings = generalSettings,
        ).initialize()
        return context
    }

    private fun FakeSystemShortcuts.lastIds(): List<String> = publishes.last().map { it.id }

    @Test
    fun `AppControl is published even when nothing is opted in`() = runTest2(autoCancel = true) {
        val shortcuts = FakeSystemShortcuts()
        start(shortcuts)
        advanceUntilIdle()

        shortcuts.publishes.size shouldBe 1
        shortcuts.lastIds() shouldBe listOf("appcontrol")
    }

    @Test
    fun `enabled clean shortcuts follow OneTap and AppControl, with matching ranks`() =
        runTest2(autoCancel = true) {
            oneTapFlow.value = true
            corpseFinderFlow.value = true
            deduplicatorFlow.value = true
            val shortcuts = FakeSystemShortcuts()
            start(shortcuts)
            advanceUntilIdle()

            val infos = shortcuts.publishes.single()
            infos.map { it.id } shouldBe listOf("onetap", "appcontrol", "clean_corpsefinder", "clean_deduplicator")
            // Launchers only show the first few entries, so the ranks have to reflect the order.
            infos.map { it.rank } shouldBe listOf(0, 1, 2, 3)
        }

    @Test
    fun `every opt-in combination is reflected in the published set`() = runTest2(autoCancel = true) {
        val shortcuts = FakeSystemShortcuts()
        start(shortcuts)
        advanceUntilIdle()
        shortcuts.lastIds() shouldBe listOf("appcontrol")

        appCleanerFlow.value = true
        advanceUntilIdle()
        shortcuts.lastIds() shouldBe listOf("appcontrol", "clean_appcleaner")

        systemCleanerFlow.value = true
        advanceUntilIdle()
        shortcuts.lastIds() shouldBe listOf("appcontrol", "clean_systemcleaner", "clean_appcleaner")

        oneTapFlow.value = true
        advanceUntilIdle()
        shortcuts.lastIds() shouldBe listOf("onetap", "appcontrol", "clean_systemcleaner", "clean_appcleaner")

        appCleanerFlow.value = false
        systemCleanerFlow.value = false
        advanceUntilIdle()
        shortcuts.lastIds() shouldBe listOf("onetap", "appcontrol")
    }

    @Test
    fun `the device shortcut cap truncates the published set`() = runTest2(autoCancel = true) {
        oneTapFlow.value = true
        corpseFinderFlow.value = true
        systemCleanerFlow.value = true
        val shortcuts = FakeSystemShortcuts(maxCount = 2)
        start(shortcuts)
        advanceUntilIdle()

        shortcuts.lastIds() shouldBe listOf("onetap", "appcontrol")
    }

    @Test
    fun `a device that allows no shortcuts gets them removed instead of an empty publish`() =
        runTest2(autoCancel = true) {
            val shortcuts = FakeSystemShortcuts(maxCount = 0)
            start(shortcuts)
            advanceUntilIdle()

            shortcuts.publishes shouldBe emptyList()
            shortcuts.removeAllCalls shouldBe 1
        }

    @Test
    fun `a rejected publish is not retried`() = runTest2(autoCancel = true) {
        // setDynamicShortcuts returns false (it does not throw) when rate limited while backgrounded.
        val shortcuts = FakeSystemShortcuts(accept = false)
        start(shortcuts)
        advanceUntilIdle()

        shortcuts.publishes.size shouldBe 1
    }

    @Test
    fun `a locale change republishes, a plain configuration change does not`() =
        runTest2(autoCancel = true) {
            val shortcuts = FakeSystemShortcuts()
            val context = start(shortcuts)
            advanceUntilIdle()
            shortcuts.publishes.size shouldBe 1

            // Rotation / dark mode / font scale: same locales, so nothing to re-resolve.
            context.changeConfiguration(
                Configuration(context.resources.configuration).apply {
                    orientation = Configuration.ORIENTATION_LANDSCAPE
                    fontScale = 2f
                },
            )
            advanceUntilIdle()
            shortcuts.publishes.size shouldBe 1

            // A per-app language change delivers no ACTION_LOCALE_CHANGED, only this.
            context.changeConfiguration(
                Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) },
            )
            advanceUntilIdle()
            // Same set, but republished so the labels re-resolve in the new language.
            shortcuts.publishes.size shouldBe 2
            shortcuts.lastIds() shouldBe listOf("appcontrol")
        }

    @Test
    fun `the configuration hook is unregistered when collection stops`() = runTest2 {
        val shortcuts = FakeSystemShortcuts()
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val context = TestContext(ApplicationProvider.getApplicationContext(), shortcuts)
        ShortcutManager(
            context = context,
            appScope = scope,
            generalSettings = generalSettings,
        ).initialize()
        context.callbacks.size shouldBe 1

        scope.cancel()
        context.callbacks shouldBe emptyList()
    }
}
