package eu.darken.sdmse.main.core.shortcuts

import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderOneClickTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorOneClickTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class OneTapCleanerTest : BaseTest() {

    @MockK lateinit var taskManager: TaskManager
    @MockK lateinit var generalSettings: GeneralSettings
    @MockK lateinit var upgradeRepo: UpgradeRepo
    private val guard = OneTapRunGuard()

    private fun <T : Any> mockSetting(value: T): DataStoreValue<T> =
        mockk<DataStoreValue<T>>(relaxed = true).apply { every { flow } returns flowOf(value) }

    private fun setup(
        pro: Boolean = true,
        corpse: Boolean = false,
        system: Boolean = false,
        app: Boolean = false,
        dedup: Boolean = false,
    ) {
        val info = mockk<UpgradeRepo.Info> {
            every { isPro } returns pro
            every { isSettled } returns true
        }
        every { upgradeRepo.upgradeInfo } returns flowOf(info)
        every { generalSettings.oneClickCorpseFinderEnabled } returns mockSetting(corpse)
        every { generalSettings.oneClickSystemCleanerEnabled } returns mockSetting(system)
        every { generalSettings.oneClickAppCleanerEnabled } returns mockSetting(app)
        every { generalSettings.oneClickDeduplicatorEnabled } returns mockSetting(dedup)
    }

    private fun create() = OneTapCleaner(taskManager, generalSettings, upgradeRepo, guard)

    @BeforeEach
    fun init() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `runOneClick returns NotPro and submits nothing when not Pro`() = runTest {
        setup(pro = false, corpse = true)
        create().runOneClick(shortcutMode = true) shouldBe OneTapCleaner.Outcome.NotPro
        coVerify(exactly = 0) { taskManager.submit(any()) }
    }

    @Test
    fun `runOneClick returns NothingEnabled when no tools are enabled`() = runTest {
        setup(pro = true)
        create().runOneClick(shortcutMode = true) shouldBe OneTapCleaner.Outcome.NothingEnabled
        coVerify(exactly = 0) { taskManager.submit(any()) }
    }

    @Test
    fun `runOneClick submits one-click tasks for enabled tools and fires onStarted`() = runTest {
        setup(pro = true, corpse = true, system = true)
        var started = false
        create().runOneClick(shortcutMode = true) { started = true } shouldBe OneTapCleaner.Outcome.Ran
        started shouldBe true
        coVerify(exactly = 1) { taskManager.submit(ofType(CorpseFinderOneClickTask::class)) }
        coVerify(exactly = 1) { taskManager.submit(ofType(SystemCleanerOneClickTask::class)) }
        coVerify(exactly = 0) { taskManager.submit(ofType(AppCleanerOneClickTask::class)) }
        coVerify(exactly = 0) { taskManager.submit(ofType(DeduplicatorOneClickTask::class)) }
    }

    @Test
    fun `runOneClick returns AlreadyRunning when a run is in progress`() = runTest {
        setup(pro = true, corpse = true)
        guard.tryStart(Job()) shouldBe true // pre-claim the single-flight guard
        create().runOneClick(shortcutMode = true) shouldBe OneTapCleaner.Outcome.AlreadyRunning
        coVerify(exactly = 0) { taskManager.submit(any()) }
    }

    @Test
    fun `runSingleTool returns NotPro, submits nothing and does not fire onStarted`() = runTest {
        setup(pro = false)
        var started = false
        create().runSingleTool(
            type = SDMTool.Type.CORPSEFINDER,
            shortcutMode = true,
        ) { started = true } shouldBe OneTapCleaner.Outcome.NotPro
        started shouldBe false
        coVerify(exactly = 0) { taskManager.submit(any()) }
    }

    @Test
    fun `runSingleTool returns AlreadyRunning and does not fire onStarted while a run is active`() = runTest {
        setup(pro = true)
        guard.tryStart(Job()) shouldBe true // pre-claim the single-flight guard
        var started = false
        create().runSingleTool(
            type = SDMTool.Type.CORPSEFINDER,
            shortcutMode = true,
        ) { started = true } shouldBe OneTapCleaner.Outcome.AlreadyRunning
        started shouldBe false
        coVerify(exactly = 0) { taskManager.submit(any()) }
    }

    @Test
    fun `runSingleTool submits that tool's one-click task and fires onStarted`() = runTest {
        setup(pro = true)
        var started = false
        create().runSingleTool(
            type = SDMTool.Type.SYSTEMCLEANER,
            shortcutMode = true,
        ) { started = true } shouldBe OneTapCleaner.Outcome.Ran
        started shouldBe true
        coVerify(exactly = 1) { taskManager.submit(ofType(SystemCleanerOneClickTask::class)) }
        coVerify(exactly = 1) { taskManager.submit(any()) }
    }

    @Test
    fun `runSingleTool maps every one-click tool to its own task`() = runTest {
        setup(pro = true)
        val cleaner = create()
        val submitted = mutableListOf<SDMTool.Task>()
        coEvery { taskManager.submit(capture(submitted)) } returns mockk(relaxed = true)

        OneTapCleaner.ONECLICK_TYPES.forEach { type ->
            cleaner.runSingleTool(type = type, shortcutMode = true) shouldBe OneTapCleaner.Outcome.Ran
        }

        submitted.map { it::class } shouldBe listOf(
            CorpseFinderOneClickTask::class,
            SystemCleanerOneClickTask::class,
            AppCleanerOneClickTask::class,
            DeduplicatorOneClickTask::class,
        )
    }

    @Test
    fun `runSingleTool ignores the one-click tool selection`() = runTest {
        // The oneClick<Tool>Enabled flags only select what the COMBINED run does. Asking for one
        // specific tool is already an explicit choice, so it runs even with everything deselected.
        setup(pro = true, corpse = false, system = false, app = false, dedup = false)
        create().runSingleTool(
            type = SDMTool.Type.DEDUPLICATOR,
            shortcutMode = true,
        ) shouldBe OneTapCleaner.Outcome.Ran
        coVerify(exactly = 1) { taskManager.submit(ofType(DeduplicatorOneClickTask::class)) }
    }

    @Test
    fun `runSingleTool propagates shortcutMode to the AppCleaner task`() = runTest {
        setup(pro = true)
        val task = slot<SDMTool.Task>()
        coEvery { taskManager.submit(capture(task)) } returns mockk(relaxed = true)

        create().runSingleTool(type = SDMTool.Type.APPCLEANER, shortcutMode = false)
        (task.captured as AppCleanerOneClickTask).shortcutMode shouldBe false

        guard.finish()
        create().runSingleTool(type = SDMTool.Type.APPCLEANER, shortcutMode = true)
        (task.captured as AppCleanerOneClickTask).shortcutMode shouldBe true
    }

    @Test
    fun `runScanOnly submits scan tasks only for enabled tools`() = runTest {
        setup(pro = true, corpse = true, app = true)
        create().runScanOnly()
        coVerify(exactly = 1) { taskManager.submit(ofType(CorpseFinderScanTask::class)) }
        coVerify(exactly = 1) { taskManager.submit(ofType(AppCleanerScanTask::class)) }
        coVerify(exactly = 0) { taskManager.submit(ofType(SystemCleanerScanTask::class)) }
        coVerify(exactly = 0) { taskManager.submit(ofType(DeduplicatorScanTask::class)) }
    }
}
