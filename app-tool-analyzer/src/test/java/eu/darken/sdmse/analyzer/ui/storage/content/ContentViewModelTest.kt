package eu.darken.sdmse.analyzer.ui.storage.content

import android.content.Context
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.categories.ContentCategory
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.analyzer.ui.ContentRoute
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
import eu.darken.sdmse.common.ui.LayoutMode
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
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

    private fun dirItem(name: String) = ContentItem(
        path = LocalPath.build("storage", "emulated", "0", name),
        lookup = null,
        itemSize = 0L,
        type = FileType.DIRECTORY,
        children = emptySet(),
        inaccessible = false,
    )

    private class Harness(
        val vm: ContentViewModel,
        val analyzer: Analyzer,
        val swiperSessionCreator: eu.darken.sdmse.common.files.SwiperSessionCreator,
        val filterEditorOptionsCreator: eu.darken.sdmse.common.files.FilterEditorOptionsCreator,
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

        val vm = ContentViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            analyzer = analyzer,
            analyzerSettings = settings,
            viewIntentTool = mockk(relaxed = true),
            exclusionManager = mockk(relaxed = true),
            filterEditorOptionsCreator = filterEditorOptionsCreator,
            upgradeRepo = mockk(relaxed = true),
            swiperSessionCreator = swiperSessionCreator,
        )
        vm.bindRoute(ContentRoute(storageId = storageId, groupId = group.id))

        // safeStateIn uses WhileSubscribed(5000) — keep a subscriber alive for the test scope's lifetime.
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }

        return Harness(vm, analyzer, swiperSessionCreator, filterEditorOptionsCreator)
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

        coVerify(exactly = 0) { h.analyzer.submit(any()) }
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
}
