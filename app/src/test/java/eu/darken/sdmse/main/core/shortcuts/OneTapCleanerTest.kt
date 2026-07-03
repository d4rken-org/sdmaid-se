package eu.darken.sdmse.main.core.shortcuts

import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderOneClickTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorOneClickTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
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
        val info = mockk<UpgradeRepo.Info> { every { isPro } returns pro }
        every { upgradeRepo.upgradeInfo } returns flowOf(info)
        every { upgradeRepo.isSettled } returns flowOf(true)
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
    fun `runScanOnly submits scan tasks only for enabled tools`() = runTest {
        setup(pro = true, corpse = true, app = true)
        create().runScanOnly()
        coVerify(exactly = 1) { taskManager.submit(ofType(CorpseFinderScanTask::class)) }
        coVerify(exactly = 1) { taskManager.submit(ofType(AppCleanerScanTask::class)) }
        coVerify(exactly = 0) { taskManager.submit(ofType(SystemCleanerScanTask::class)) }
        coVerify(exactly = 0) { taskManager.submit(ofType(DeduplicatorScanTask::class)) }
    }
}
