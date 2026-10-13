package eu.darken.sdmse.deduplicator.ui.details

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.ViewIntentTool
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.ui.DeduplicatorDetailsRoute
import eu.darken.sdmse.deduplicator.ui.preview.previewChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewCluster
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.mockDataStoreValue

class DeduplicatorDetailsScreenHostTest : BaseComposeRobolectricTest() {

    private val directoryLabel = "/storage/emulated/0/Pictures"

    // Scanned shape: one favorite group with a keeper, every file in the same folder.
    private val duplicates = listOf("host-a.jpg", "host-b.jpg", "host-c.jpg").map { name ->
        previewChecksumDuplicate(
            pathSegments = arrayOf("storage", "emulated", "0", "Pictures", name),
            size = 1024L,
            hashSeed = "host",
        )
    }
    private val group = ChecksumDuplicate.Group(
        duplicates = duplicates.toSet(),
        identifier = Duplicate.Group.Id("host-group"),
        keeperIdentifier = duplicates.first().identifier,
    )
    private val cluster = previewCluster(
        identifier = Duplicate.Cluster.Id("host"),
        groups = setOf(group),
        favoriteGroupIdentifier = group.identifier,
    )

    private val taskSubmitter = mockk<TaskSubmitter>(relaxed = true).apply {
        every { state } returns MutableStateFlow(TaskSubmitter.State(tasks = emptySet()))
    }

    private fun buildVm(): DeduplicatorDetailsViewModel {
        val deduplicator = mockk<Deduplicator>(relaxed = true).apply {
            every { state } returns MutableStateFlow(
                Deduplicator.State(data = Deduplicator.Data(clusters = setOf(cluster)), progress = null),
            )
            every { progress } returns MutableStateFlow(null)
        }
        val directoryView = mockDataStoreValue(true)
        val allowDeleteAllValue = mockDataStoreValue(false)
        val settings = mockk<DeduplicatorSettings>().apply {
            every { isDirectoryViewEnabled } returns directoryView
            every { allowDeleteAll } returns allowDeleteAllValue
        }
        val info = mockk<UpgradeRepo.Info>().apply {
            every { isPro } returns true
            every { isSettled } returns true
        }
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns flowOf(info)
        }
        return DeduplicatorDetailsViewModel(
            handle = SavedStateHandle(),
            dispatcherProvider = TestDispatcherProvider(),
            deduplicator = deduplicator,
            taskSubmitter = taskSubmitter,
            settings = settings,
            upgradeRepo = upgradeRepo,
            viewIntentTool = mockk<ViewIntentTool>(relaxed = true),
        )
    }

    private fun mockTourController(): GuidedTourController = mockk<GuidedTourController>(relaxed = true).apply {
        coEvery { shouldStart(any()) } returns false
        every { session } returns MutableStateFlow(null)
    }

    @Test
    fun `tapping a folder holding the whole set confirms and submits a capped folder delete`() {
        // The real ViewModel instance, passed explicitly: hiltViewModel() has nothing to resolve here.
        val vm = buildVm()
        composeRule.setContent {
            CompositionLocalProvider(LocalGuidedTourController provides mockTourController()) {
                PreviewWrapper {
                    DeduplicatorDetailsScreenHost(
                        route = DeduplicatorDetailsRoute(identifier = cluster.identifier),
                        vm = vm,
                    )
                }
            }
        }

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(directoryLabel))
        composeRule.onNode(hasClickAction() and hasText(directoryLabel)).performClick()

        composeRule.onNodeWithText("Delete 2 selected items?").assertExists()
        composeRule.onAllNodes(isToggleable() and hasAnyAncestor(isDialog())).assertCountEquals(0)

        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        composeRule.waitForIdle()

        coVerify(exactly = 1) {
            taskSubmitter.submit(
                DeduplicatorDeleteTask(
                    mode = DeduplicatorDeleteTask.TargetMode.Directory(
                        clusterId = cluster.identifier,
                        targets = duplicates.map { it.identifier }.toSet(),
                        deleteAll = false,
                    ),
                ),
            )
        }
        coVerify(exactly = 1) { taskSubmitter.submit(any()) }
    }
}
