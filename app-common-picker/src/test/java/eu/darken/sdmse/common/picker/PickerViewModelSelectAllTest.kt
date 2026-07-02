package eu.darken.sdmse.common.picker

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

class PickerViewModelSelectAllTest : BaseTest() {

    private val areaPath = LocalPath.build("area")
    private val area = DataArea(
        path = areaPath,
        type = DataArea.Type.SDCARD,
        userHandle = UserHandle2(0),
    )

    private fun lookupOf(path: LocalPath, dir: Boolean = true) = LocalPathLookup(
        lookedUp = path,
        fileType = if (dir) FileType.DIRECTORY else FileType.FILE,
        size = 0L,
        modifiedAt = Instant.EPOCH,
        target = null,
    )

    private class Harness(
        val vm: PickerViewModel,
        val resourceScope: CoroutineScope,
    )

    private fun harness(listings: Map<LocalPath, List<LocalPathLookup>>): Harness {
        val resourceScope = CoroutineScope(SupervisorJob())
        val gatewaySwitch = mockk<GatewaySwitch> {
            every { sharedResource } returns SharedResource.createKeepAlive("test:gateway", resourceScope)
            coEvery { lookup(any()) } answers { lookupOf(arg<APath>(0) as LocalPath) }
            coEvery { lookupFiles(any()) } answers { listings[arg<APath>(0) as LocalPath] ?: emptyList() }
        }
        val dataAreaManager = mockk<DataAreaManager> {
            every { state } returns flowOf(DataAreaManager.State(areas = setOf(area)))
        }
        val vm = PickerViewModel(
            handle = SavedStateHandle(),
            dispatcherProvider = TestDispatcherProvider(),
            dataAreaManager = dataAreaManager,
            navCtrl = mockk<NavigationController>(relaxed = true),
            gatewaySwitch = gatewaySwitch,
        )
        return Harness(vm, resourceScope)
    }

    private suspend fun PickerViewModel.openRow(path: LocalPath) {
        val row = state.first { st -> st.items.any { it.item.lookup.lookedUp == path } }
            .items.first { it.item.lookup.lookedUp == path }
        onRowClick(row)
    }

    @Test
    fun `selectAll keeps rows the user already hand-picked`() = runTest2 {
        // Regression guard: selectAll routed every listed item through the select() TOGGLE, so
        // rows picked by hand before tapping "Select all" got DEselected by it.
        val f1 = lookupOf(areaPath.child("f1"), dir = false)
        val f2 = lookupOf(areaPath.child("f2"), dir = false)
        val f3 = lookupOf(areaPath.child("f3"), dir = false)
        val h = harness(mapOf(areaPath to listOf(f1, f2, f3)))
        try {
            h.vm.setRequest(PickerRequest(requestKey = "test", mode = PickerRequest.PickMode.FILES_AND_DIRS))
            advanceUntilIdle()
            h.vm.openRow(areaPath)
            advanceUntilIdle()

            h.vm.select(listOf(f1))
            advanceUntilIdle()

            h.vm.selectAll()
            advanceUntilIdle()

            h.vm.state.first().selected.map { it.lookup.lookedUp } shouldContainExactlyInAnyOrder
                listOf(f1, f2, f3).map { it.lookedUp }
        } finally {
            h.resourceScope.cancel()
        }
    }

    @Test
    fun `selectAll keeps a selected ancestor instead of narrowing it to the listed children`() = runTest2 {
        // A selected parent covers everything below it. Routing its listed children through the
        // select() toggle would swap the parent for just those children, silently dropping the
        // rest of the parent's coverage.
        val parentPath = areaPath.child("parent")
        val parent = lookupOf(parentPath, dir = true)
        val c1 = lookupOf(parentPath.child("c1"), dir = false)
        val c2 = lookupOf(parentPath.child("c2"), dir = false)
        val h = harness(
            mapOf(
                areaPath to listOf(parent),
                parentPath to listOf(c1, c2),
            ),
        )
        try {
            h.vm.setRequest(PickerRequest(requestKey = "test", mode = PickerRequest.PickMode.FILES_AND_DIRS))
            advanceUntilIdle()
            h.vm.openRow(areaPath)
            advanceUntilIdle()

            h.vm.select(listOf(parent))
            advanceUntilIdle()

            h.vm.openRow(parentPath)
            advanceUntilIdle()

            h.vm.selectAll()
            advanceUntilIdle()

            h.vm.state.first().selected.map { it.lookup.lookedUp } shouldContainExactlyInAnyOrder
                listOf(parent.lookedUp)
        } finally {
            h.resourceScope.cancel()
        }
    }

    @Test
    fun `selectAll is a no-op in single-selection mode`() = runTest2 {
        // DIR mode is single-selection (e.g. the path-exclusion picker); "select everything"
        // has no meaning there and used to replace the selection with the first listed row.
        val d1 = lookupOf(areaPath.child("d1"))
        val d2 = lookupOf(areaPath.child("d2"))
        val h = harness(mapOf(areaPath to listOf(d1, d2)))
        try {
            h.vm.setRequest(PickerRequest(requestKey = "test", mode = PickerRequest.PickMode.DIR))
            advanceUntilIdle()
            h.vm.openRow(areaPath)
            advanceUntilIdle()

            h.vm.select(listOf(d2))
            advanceUntilIdle()

            h.vm.selectAll()
            advanceUntilIdle()

            val state = h.vm.state.first()
            state.selected.map { it.lookup.lookedUp } shouldContainExactlyInAnyOrder listOf(d2.lookedUp)
            state.allowSelectAll shouldBe false
        } finally {
            h.resourceScope.cancel()
        }
    }

    @Test
    fun `selectAll still consolidates when a listed ancestor covers selected children`() = runTest2 {
        // The reverse direction stays as-is: selecting a listed directory replaces its already
        // selected descendants (the dir row covers them all).
        val parentPath = areaPath.child("parent")
        val parent = lookupOf(parentPath, dir = true)
        val c1 = lookupOf(parentPath.child("c1"), dir = false)
        val h = harness(
            mapOf(
                areaPath to listOf(parent),
                parentPath to listOf(c1),
            ),
        )
        try {
            h.vm.setRequest(PickerRequest(requestKey = "test", mode = PickerRequest.PickMode.FILES_AND_DIRS))
            advanceUntilIdle()
            h.vm.openRow(areaPath)
            advanceUntilIdle()

            h.vm.select(listOf(c1))
            advanceUntilIdle()

            h.vm.selectAll()
            advanceUntilIdle()

            h.vm.state.first().selected.map { it.lookup.lookedUp } shouldContainExactlyInAnyOrder
                listOf(parent.lookedUp)
        } finally {
            h.resourceScope.cancel()
        }
    }
}
