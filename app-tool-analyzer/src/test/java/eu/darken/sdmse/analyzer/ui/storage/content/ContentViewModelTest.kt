package eu.darken.sdmse.analyzer.ui.storage.content

import android.content.Context
import android.content.Intent
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.analyzer.core.content.ContentDeleteTask
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.categories.ContentCategory
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.analyzer.ui.ContentRoute
import eu.darken.sdmse.common.ViewIntentTool
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.util.UUID

class ContentViewModelTest : BaseTest() {

    private val storageId = StorageId(
        internalId = null,
        externalId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    )

    private val context = mockk<Context>().apply {
        every { getString(R.string.analyzer_storage_content_type_system_info) } returns "SYSTEM_INFO"
        every { getString(R.string.analyzer_storage_content_type_media_readonly_info) } returns "MEDIA_READONLY"
    }

    private fun storage() = DeviceStorage(
        id = storageId,
        label = "Internal".toCaString(),
        type = DeviceStorage.Type.PRIMARY,
        hardware = DeviceStorage.Hardware.BUILT_IN,
        spaceCapacity = 100L,
        spaceFree = 50L,
        setupIncomplete = false,
    )

    private fun dirItem(name: String, type: FileType = FileType.DIRECTORY) = ContentItem(
        path = LocalPath.build("storage", "emulated", "0", name),
        lookup = null,
        itemSize = 0L,
        type = type,
        children = emptySet(),
        inaccessible = false,
    )

    private class Harness(
        val vm: ContentViewModel,
        val analyzer: Analyzer,
        val swiperSessionCreator: eu.darken.sdmse.common.files.SwiperSessionCreator,
        val filterEditorOptionsCreator: eu.darken.sdmse.common.files.FilterEditorOptionsCreator,
        val viewIntentTool: ViewIntentTool,
        val exclusionManager: ExclusionManager,
        val taskSubmitter: TaskSubmitter,
    )

    private fun TestScope.harness(
        category: ContentCategory,
        group: ContentGroup,
    ): Harness {
        val dataFlow = MutableStateFlow(
            Analyzer.Data(
                storages = setOf(storage()),
                categories = mapOf(storageId to listOf(category)),
                groups = mapOf(group.id to group),
            ),
        )
        val analyzer = mockk<Analyzer>(relaxed = true).apply {
            every { data } returns dataFlow
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
        }
        val settings = mockk<AnalyzerSettings>().apply {
            every { contentLayoutMode } returns mockk { every { flow } returns flowOf(LayoutMode.LINEAR) }
        }
        val swiperSessionCreator = mockk<eu.darken.sdmse.common.files.SwiperSessionCreator>(relaxed = true)
        val filterEditorOptionsCreator =
            mockk<eu.darken.sdmse.common.files.FilterEditorOptionsCreator>(relaxed = true)
        val viewIntentTool = mockk<ViewIntentTool>(relaxed = true)
        val exclusionManager = mockk<ExclusionManager>(relaxed = true)
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)

        val vm = ContentViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            analyzer = analyzer,
            analyzerSettings = settings,
            viewIntentTool = viewIntentTool,
            exclusionManager = exclusionManager,
            filterEditorOptionsCreator = filterEditorOptionsCreator,
            upgradeRepo = mockk(relaxed = true),
            swiperSessionCreator = swiperSessionCreator,
            taskSubmitter = taskSubmitter,
        )
        vm.bindRoute(ContentRoute(storageId = storageId, groupId = group.id))

        // safeStateIn uses WhileSubscribed(5000) — keep a subscriber alive for the test scope's lifetime.
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }

        return Harness(
            vm,
            analyzer,
            swiperSessionCreator,
            filterEditorOptionsCreator,
            viewIntentTool,
            exclusionManager,
            taskSubmitter,
        )
    }

    private fun ContentViewModel.readyState(): ContentViewModel.State.Ready {
        val state = state.value
        state.shouldBeInstanceOf<ContentViewModel.State.Ready>()
        return state
    }

    @Test
    fun `system group shows the system info banner at the top level`() = runTest2 {
        val group = ContentGroup(label = "System".toCaString())
        val h = harness(SystemCategory(storageId, setOf(group)), group)
        advanceUntilIdle()

        val state = h.vm.readyState()
        state.isReadOnly shouldBe true
        state.infoBanner!!.get(context) shouldBe "SYSTEM_INFO"
    }

    @Test
    fun `read-only media shows the media info banner`() = runTest2 {
        val group = ContentGroup(label = "Media".toCaString())
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = true), group)
        advanceUntilIdle()

        val state = h.vm.readyState()
        state.isReadOnly shouldBe true
        state.infoBanner!!.get(context) shouldBe "MEDIA_READONLY"
    }

    @Test
    fun `writable media shows no banner and is not read-only`() = runTest2 {
        val group = ContentGroup(label = "Media".toCaString())
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = false), group)
        advanceUntilIdle()

        val state = h.vm.readyState()
        state.isReadOnly shouldBe false
        state.infoBanner.shouldBeNull()
    }

    @Test
    fun `system banner hides while browsing into a folder but stays read-only`() = runTest2 {
        val child = dirItem("Android")
        val group = ContentGroup(label = "System".toCaString(), contents = setOf(child))
        val h = harness(SystemCategory(storageId, setOf(group)), group)
        advanceUntilIdle()

        h.vm.onItemClick(ContentViewModel.Item(parent = null, content = child, sizeRatio = null))
        advanceUntilIdle()

        val state = h.vm.readyState()
        state.infoBanner.shouldBeNull()
        // Only the banner is level-gated — the delete/filter/Swiper guard must not lift mid-browse.
        state.isReadOnly shouldBe true
    }

    @Test
    fun `app group shows no banner and is writable`() = runTest2 {
        val group = ContentGroup(label = "App data".toCaString())
        val installId = InstallId(pkgId = Pkg.Id("com.example.app"), userHandle = UserHandle2(0))
        val pkgStat = AppCategory.PkgStat(
            pkg = mockk<Installed>().apply { every { this@apply.installId } returns installId },
            isShallow = false,
            appCode = null,
            appData = group,
            appMedia = null,
            extraData = null,
        )
        val h = harness(AppCategory(storageId, pkgStats = mapOf(installId to pkgStat)), group)
        advanceUntilIdle()

        val state = h.vm.readyState()
        state.isReadOnly shouldBe false
        state.infoBanner.shouldBeNull()
    }

    @Test
    fun `read-only media banner persists while browsing into a folder`() = runTest2 {
        val child = dirItem("DCIM")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(child))
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = true), group)
        advanceUntilIdle()

        h.vm.onItemClick(ContentViewModel.Item(parent = null, content = child, sizeRatio = null))
        advanceUntilIdle()

        h.vm.readyState().infoBanner!!.get(context) shouldBe "MEDIA_READONLY"
    }

    @Test
    fun `delete is blocked on read-only media`() = runTest2 {
        val group = ContentGroup(label = "Media".toCaString())
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = true), group)
        advanceUntilIdle()

        h.vm.onDeleteSelected(setOf(dirItem("DCIM")))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any(), any()) }
    }

    @Test
    fun `delete on writable content goes through the task submitter`() = runTest2 {
        val child = dirItem("DCIM")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(child))
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = false), group)
        val submitted = slot<SDMTool.Task>()
        coEvery { h.taskSubmitter.submit(capture(submitted), any()) } returns ContentDeleteTask.Result(
            affectedSpace = 1024L,
            affectedPaths = setOf(child.path),
        )
        advanceUntilIdle()

        h.vm.onDeleteSelected(setOf(child))
        advanceUntilIdle()

        // ContentDeleteTask is Reportable, so it has to reach the task manager to be counted by stats.
        val task = submitted.captured
        task.shouldBeInstanceOf<ContentDeleteTask>()
        task.storageId shouldBe storageId
        task.groupId shouldBe group.id
        task.targets shouldBe setOf(child.path)

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<ContentViewModel.Event.ContentDeleted>()
        event.count shouldBe 1
        event.freedSpace shouldBe 1024L
    }

    @Test
    fun `filter creation is blocked on read-only media`() = runTest2 {
        val group = ContentGroup(label = "Media".toCaString())
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = true), group)
        advanceUntilIdle()

        h.vm.onCreateFilter(setOf(dirItem("DCIM")))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.filterEditorOptionsCreator.createOptions(any()) }
    }

    @Test
    fun `swiper session creation is blocked on read-only media`() = runTest2 {
        val group = ContentGroup(label = "Media".toCaString())
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = true), group)
        advanceUntilIdle()

        h.vm.onCreateSwiperSession(setOf(dirItem("DCIM")))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.swiperSessionCreator.createSession(any()) }
    }

    @Test
    fun `there is no external folder at the group root`() = runTest2 {
        val child = dirItem("DCIM")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(child))
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = false), group)
        coEvery { h.viewIntentTool.canOpenFolder(any()) } returns true
        advanceUntilIdle()

        h.vm.readyState().externalFolder.shouldBeNull()
    }

    @Test
    fun `the browsed folder is offered when it can be opened externally`() = runTest2 {
        val child = dirItem("DCIM")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(child))
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = false), group)
        coEvery { h.viewIntentTool.canOpenFolder(child.path) } returns true
        advanceUntilIdle()

        h.vm.onItemClick(ContentViewModel.Item(parent = null, content = child, sizeRatio = null))
        advanceUntilIdle()

        h.vm.readyState().externalFolder shouldBe child.path
    }

    @Test
    fun `the browsed folder is not offered when it can not be opened externally`() = runTest2 {
        val child = dirItem("DCIM")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(child))
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = false), group)
        coEvery { h.viewIntentTool.canOpenFolder(child.path) } returns false
        advanceUntilIdle()

        h.vm.onItemClick(ContentViewModel.Item(parent = null, content = child, sizeRatio = null))
        advanceUntilIdle()

        h.vm.readyState().externalFolder.shouldBeNull()
    }

    @Test
    fun `symbolic links are never offered as external folders`() = runTest2 {
        val child = dirItem("link", type = FileType.SYMBOLIC_LINK)
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(child))
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = false), group)
        coEvery { h.viewIntentTool.canOpenFolder(any()) } returns true
        advanceUntilIdle()

        h.vm.onItemClick(ContentViewModel.Item(parent = null, content = child, sizeRatio = null))
        advanceUntilIdle()

        h.vm.readyState().externalFolder.shouldBeNull()
    }

    @Test
    fun `unknown item types are never offered as external folders`() = runTest2 {
        val child = dirItem("mystery", type = FileType.UNKNOWN)
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(child))
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = false), group)
        coEvery { h.viewIntentTool.canOpenFolder(any()) } returns true
        advanceUntilIdle()

        h.vm.onItemClick(ContentViewModel.Item(parent = null, content = child, sizeRatio = null))
        advanceUntilIdle()

        h.vm.readyState().externalFolder.shouldBeNull()
    }

    @Test
    fun `opening externally emits the intent from the view intent tool`() = runTest2 {
        val child = dirItem("DCIM")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(child))
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = false), group)
        val intent = mockk<Intent>()
        coEvery { h.viewIntentTool.createForFolder(child.path) } returns intent
        advanceUntilIdle()

        h.vm.onOpenExternally(child.path)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<ContentViewModel.Event.OpenContent>()
        event.intent shouldBe intent
    }

    @Test
    fun `opening externally reports when no app can handle the folder`() = runTest2 {
        val child = dirItem("DCIM")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(child))
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = false), group)
        coEvery { h.viewIntentTool.createForFolder(child.path) } returns null
        advanceUntilIdle()

        h.vm.onOpenExternally(child.path)
        advanceUntilIdle()

        h.vm.events.first() shouldBe ContentViewModel.Event.NoExternalAppFound
    }

    @Test
    fun `exclusion is blocked on read-only content`() = runTest2 {
        // The UI hides the exclude action for read-only content, but the handler must refuse too:
        // excluding paths the user can neither delete nor filter only creates dead exclusions.
        val group = ContentGroup(label = "System".toCaString())
        val h = harness(SystemCategory(storageId, setOf(group)), group)
        advanceUntilIdle()

        h.vm.onExcludeSelected(setOf(dirItem("data")))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.exclusionManager.save(any<Set<Exclusion>>()) }
    }

    @Test
    fun `exclusion is allowed on writable content`() = runTest2 {
        val group = ContentGroup(label = "Media".toCaString())
        val h = harness(MediaCategory(storageId, setOf(group), isReadOnly = false), group)
        advanceUntilIdle()

        h.vm.onExcludeSelected(setOf(dirItem("DCIM")))
        advanceUntilIdle()

        coVerify(exactly = 1) { h.exclusionManager.save(any<Set<Exclusion>>()) }
    }
}
