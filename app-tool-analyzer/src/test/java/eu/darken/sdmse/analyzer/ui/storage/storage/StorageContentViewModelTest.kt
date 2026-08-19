package eu.darken.sdmse.analyzer.ui.storage.storage

import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.ContentCategory
import eu.darken.sdmse.analyzer.core.storage.categories.OtherUsersCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.analyzer.ui.ContentRoute
import eu.darken.sdmse.analyzer.ui.StorageContentRoute
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.util.UUID

class StorageContentViewModelTest : BaseTest() {

    private val storageId = StorageId(
        internalId = null,
        externalId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    )

    private fun storage() = DeviceStorage(
        id = storageId,
        label = "Internal".toCaString(),
        type = DeviceStorage.Type.PRIMARY,
        hardware = DeviceStorage.Hardware.BUILT_IN,
        spaceCapacity = 100L,
        spaceFree = 50L,
        setupIncomplete = false,
    )

    private data class UserSpec(
        val handleId: Int,
        val isBrowsable: Boolean = true,
        val appDataKnown: Boolean = true,
        val sharedMediaKnown: Boolean = true,
    )

    private fun userEntry(
        spec: UserSpec,
        groupId: ContentGroup.Id,
    ) = OtherUsersCategory.UserEntry(
        handle = UserHandle2(spec.handleId),
        label = "User-${spec.handleId}".toCaString(),
        groupId = groupId,
        appDataKnown = spec.appDataKnown,
        sharedMediaKnown = spec.sharedMediaKnown,
        isBrowsable = spec.isBrowsable,
    )

    private fun TestScope.harness(vararg categories: ContentCategory): StorageContentViewModel {
        val dataFlow = MutableStateFlow(
            Analyzer.Data(
                storages = setOf(storage()),
                categories = mapOf(storageId to categories.toList()),
                groups = categories.flatMap { it.groups }.associateBy { it.id },
            ),
        )
        val analyzer = mockk<Analyzer>(relaxed = true).apply {
            every { data } returns dataFlow
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
        }
        val vm = StorageContentViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            analyzer = analyzer,
            taskSubmitter = mockk<TaskSubmitter>(relaxed = true),
        )
        vm.bindRoute(StorageContentRoute(storageId = storageId))

        // safeStateIn uses WhileSubscribed(5000) — keep a subscriber alive for the test scope's lifetime.
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }

        return vm
    }

    private class CollectedNavEvents(val list: MutableList<NavEvent>, private val job: Job) {
        fun cancel() = job.cancel()
    }

    /**
     * Must be called on the test scope itself, NOT on [TestScope.backgroundScope]: advanceUntilIdle()
     * stops as soon as only background work is left, so a collector living in backgroundScope never
     * gets resumed and every assertion would read an empty list — including the "no navigation"
     * ones, which would then pass vacuously.
     */
    private fun CoroutineScope.collectNavEvents(vm: StorageContentViewModel): CollectedNavEvents {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.navEvents.collect { list.add(it) } }
        return CollectedNavEvents(list, job)
    }

    private fun category(vararg users: Pair<Int, Boolean>): OtherUsersCategory = categoryOf(
        *users
            .map { (handleId, browsable) ->
                UserSpec(handleId = handleId, isBrowsable = browsable, sharedMediaKnown = browsable)
            }
            .toTypedArray(),
    )

    private fun categoryOf(vararg specs: UserSpec): OtherUsersCategory {
        val groups = specs.map { ContentGroup(label = "User-${it.handleId}".toCaString()) }
        return OtherUsersCategory(
            storageId = storageId,
            groups = groups,
            users = specs.mapIndexed { index, spec -> userEntry(spec, groups[index].id) },
        )
    }

    private fun systemCategory() = SystemCategory(
        storageId = storageId,
        groups = listOf(ContentGroup(label = "System".toCaString())),
    )

    private fun StorageContentViewModel.systemRow(): StorageContentViewModel.Row.System {
        val ready = state.value as StorageContentViewModel.State.Ready
        return ready.rows!!.filterIsInstance<StorageContentViewModel.Row.System>().single()
    }

    @Test
    fun `a browsable user navigates to its own content group`() = runTest2 {
        // One group per user, so the route must carry that user's group, not the first one.
        val category = category(10 to true, 11 to true)
        val vm = harness(category)
        val events = collectNavEvents(vm)
        advanceUntilIdle()

        val second = category.users.last()
        vm.onUserClick(second)
        advanceUntilIdle()

        events.list.single() shouldBe NavEvent.GoTo(
            destination = ContentRoute(storageId = storageId, groupId = second.groupId, installId = null),
        )
        events.cancel()
    }

    @Test
    fun `a non-browsable user does not navigate`() = runTest2 {
        val category = category(10 to false)
        val vm = harness(category)
        val events = collectNavEvents(vm)
        advanceUntilIdle()

        vm.onUserClick(category.users.single())
        advanceUntilIdle()

        events.list.shouldBeEmpty()
        events.cancel()
    }

    @Test
    fun `clicking the card itself does not navigate`() = runTest2 {
        val category = category(10 to true)
        val vm = harness(category)
        val events = collectNavEvents(vm)
        advanceUntilIdle()

        vm.onCategoryClick(StorageContentViewModel.Row.OtherUsers(storage = storage(), category = category))
        advanceUntilIdle()

        events.list.shouldBeEmpty()
        events.cancel()
    }

    @Test
    fun `system row admits other users when shared media is unmeasured`() = runTest2 {
        // Stats tier: app data is exact, shared media isn't measured at all.
        val others = categoryOf(UserSpec(handleId = 10, sharedMediaKnown = false))
        val vm = harness(systemCategory(), others)
        advanceUntilIdle()

        vm.systemRow().hidesOtherUsers shouldBe true
    }

    @Test
    fun `system row admits other users when app data is unmeasured`() = runTest2 {
        val others = categoryOf(
            UserSpec(handleId = 10),
            UserSpec(handleId = 11, isBrowsable = false, appDataKnown = false, sharedMediaKnown = false),
        )
        val vm = harness(systemCategory(), others)
        advanceUntilIdle()

        vm.systemRow().hidesOtherUsers shouldBe true
    }

    @Test
    fun `system row stays quiet when every other user is fully measured`() = runTest2 {
        // Root tier: both flags are true for every user, so there is nothing hidden to admit.
        val others = categoryOf(UserSpec(handleId = 10), UserSpec(handleId = 11))
        val vm = harness(systemCategory(), others)
        advanceUntilIdle()

        vm.systemRow().hidesOtherUsers shouldBe false
    }

    @Test
    fun `system row stays quiet without other users`() = runTest2 {
        val vm = harness(systemCategory())
        advanceUntilIdle()

        vm.systemRow().hidesOtherUsers shouldBe false
    }
}
