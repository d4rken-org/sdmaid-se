package eu.darken.sdmse.main.ui.areas

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

@ExtendWith(MockKExtension::class)
class DataAreasViewModelTest : BaseTest() {

    @MockK lateinit var dataAreaManager: DataAreaManager
    @MockK lateinit var taskManager: TaskManager
    @MockK lateinit var webpageTool: WebpageTool

    private lateinit var areaState: MutableStateFlow<DataAreaManager.State>
    private lateinit var taskState: MutableStateFlow<TaskSubmitter.State>

    @BeforeEach
    fun setup() {
        areaState = MutableStateFlow(DataAreaManager.State(emptySet()))
        taskState = MutableStateFlow(TaskSubmitter.State())

        every { dataAreaManager.state } returns areaState
        every { taskManager.state } returns taskState
        every { webpageTool.open(any()) } returns Unit
        coEvery { dataAreaManager.reloadAndAwait() } returns DataAreaManager.State(
            areas = emptySet(),
            refreshGeneration = 1L,
        )
    }

    private fun buildVM() = DataAreasViewModel(
        handle = SavedStateHandle(),
        dispatcherProvider = TestDispatcherProvider(),
        dataAreaManager = dataAreaManager,
        taskManager = taskManager,
        webpageTool = webpageTool,
    )

    @Test
    fun `state is idle before reload`() = runTest2 {
        val vm = buildVM()

        vm.state.first { it.areas != null } shouldBe DataAreasViewModel.State(
            areas = emptySet(),
            allowReload = true,
            isReloading = false,
        )
    }

    @Test
    fun `reload exposes progress until refreshed state returns`() = runTest2 {
        val reloadResult = CompletableDeferred<DataAreaManager.State>()
        coEvery { dataAreaManager.reloadAndAwait() } coAnswers { reloadResult.await() }
        val vm = buildVM()

        vm.state.first { it.allowReload }
        vm.reloadDataAreas()

        vm.state.first { it.isReloading } shouldBe DataAreasViewModel.State(
            areas = emptySet(),
            allowReload = false,
            isReloading = true,
        )

        reloadResult.complete(DataAreaManager.State(areas = emptySet(), refreshGeneration = 1L))

        vm.state.first { !it.isReloading && it.allowReload } shouldBe DataAreasViewModel.State(
            areas = emptySet(),
            allowReload = true,
            isReloading = false,
        )
    }

    @Test
    fun `reload progress resets after failure`() = runTest2 {
        val error = IllegalStateException("reload failed")
        coEvery { dataAreaManager.reloadAndAwait() } throws error
        val vm = buildVM()

        vm.reloadDataAreas()

        vm.state.first { !it.isReloading && it.allowReload }
        vm.errorEvents.first() shouldBe error
    }

    @Test
    fun `duplicate reload taps do not overlap`() = runTest2 {
        val reloadResult = CompletableDeferred<DataAreaManager.State>()
        coEvery { dataAreaManager.reloadAndAwait() } coAnswers { reloadResult.await() }
        val vm = buildVM()

        vm.reloadDataAreas()
        vm.state.first { it.isReloading }

        vm.reloadDataAreas()

        coVerify(exactly = 1) { dataAreaManager.reloadAndAwait() }

        reloadResult.complete(DataAreaManager.State(areas = emptySet(), refreshGeneration = 1L))
        vm.state.first { !it.isReloading }
    }
}
