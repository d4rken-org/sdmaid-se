package eu.darken.sdmse.deduplicator.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import eu.darken.sdmse.common.coil.LocalPreviewImageProvider
import eu.darken.sdmse.common.coil.PreviewImageProvider
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewCluster
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class DeduplicatorListScreenTest : BaseComposeRobolectricTest() {

    private fun clusterRow(
        name: String,
        size: Long = 1024L,
        withKeeper: Boolean = false,
    ): DeduplicatorListViewModel.DeduplicatorListRow {
        val duplicates = setOf(
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", "emulated", "0", "Pictures", "$name-a.jpg"),
                size = size,
                hashSeed = "$name-a",
            ),
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", "emulated", "0", "Pictures", "$name-b.jpg"),
                size = size,
                hashSeed = "$name-b",
            ),
        )
        // The keeper is deliberately the SECOND copy, which differs from `cluster.previewFile`
        // (the first), so a tile that still shows the arbitrary preview file is detectable.
        val keeper = if (withKeeper) duplicates.last() else null
        val group = ChecksumDuplicate.Group(
            duplicates = duplicates,
            identifier = Duplicate.Group.Id("$name-group"),
            keeperIdentifier = keeper?.identifier,
        )
        val cluster = previewCluster(
            identifier = Duplicate.Cluster.Id(name),
            groups = setOf(group),
            favoriteGroupIdentifier = if (keeper != null) group.identifier else null,
        )
        val targetIds = duplicates.filter { it.identifier != keeper?.identifier }.map { it.identifier }.toSet()
        return DeduplicatorListViewModel.DeduplicatorListRow(
            cluster = cluster,
            deleteTargetIds = targetIds,
            freeableSize = duplicates.filter { it.identifier in targetIds }.sumOf { it.size },
            keeper = keeper,
        )
    }

    private fun ComposeContentTestRule.setListScreen(state: DeduplicatorListViewModel.State?) {
        setContent {
            PreviewWrapper {
                DeduplicatorListScreen(stateSource = MutableStateFlow(state))
            }
        }
    }

    @Test
    fun `loading state shows no rows or empty marker`() {
        composeRule.setListScreen(null)

        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
        composeRule.onNodeWithText("Deduplicator").assertExists()
    }

    @Test
    fun `empty state shows the Empty placeholder`() {
        composeRule.setListScreen(
            DeduplicatorListViewModel.State(rows = emptyList(), layoutMode = LayoutMode.GRID),
        )

        composeRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun `populated GRID state shows the layout toggle action`() {
        // In GRID, the action icon is the "switch to linear" variant. Its content description
        // is `general_toggle_layout_mode` either way ("Switch view mode").
        composeRule.setListScreen(
            DeduplicatorListViewModel.State(
                rows = listOf(clusterRow("vacation")),
                layoutMode = LayoutMode.GRID,
            ),
        )

        composeRule.onNodeWithContentDescription("Switch view mode").assertExists()
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
    }

    @Test
    fun `populated LINEAR state renders the actual row content with the duplicate file name`() {
        // Verifies LinearList composed an actual row body — not just that the empty placeholder
        // is absent. LINEAR rather than GRID because the linear row template surfaces the file
        // name as plain text; GRID uses an icon + chip layout.
        composeRule.setListScreen(
            DeduplicatorListViewModel.State(
                rows = listOf(clusterRow("vacation")),
                layoutMode = LayoutMode.LINEAR,
            ),
        )

        composeRule.onNodeWithContentDescription("Switch view mode").assertExists()
        composeRule.onNodeWithText("vacation-a.jpg").assertExists()
    }

    @Test
    fun `tapping the layout toggle invokes onToggleLayoutMode`() {
        var toggled = 0
        composeRule.setContent {
            PreviewWrapper {
                DeduplicatorListScreen(
                    stateSource = MutableStateFlow(
                        DeduplicatorListViewModel.State(
                            rows = listOf(clusterRow("vacation")),
                            layoutMode = LayoutMode.GRID,
                        ),
                    ),
                    onToggleLayoutMode = { toggled++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Switch view mode").performClick()

        // Compose state-event callbacks can fire synchronously inside an onClick lambda. Assert
        // at-least-once to stay robust to dispatcher changes.
        if (toggled < 1) throw AssertionError("Expected at least one toggle, got $toggled")
    }

    // ─────────────────────────── per-region interaction ───────────────────────────

    // Targets a card region by the a11y click label set via combinedClickable(onClickLabel = …),
    // so tests assert the accessible action too. Labels are unique within a single layout mode.
    private fun hasClickLabel(label: String) = SemanticsMatcher("OnClick label == '$label'") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun ComposeContentTestRule.setScreen(
        state: DeduplicatorListViewModel.State,
        onClusterDelete: (Duplicate.Cluster) -> Unit = {},
        onClusterClick: (Duplicate.Cluster) -> Unit = {},
        onClusterPreview: (Duplicate.Cluster) -> Unit = {},
        onDuplicateClick: (Duplicate.Cluster, Duplicate) -> Unit = { _, _ -> },
        onDuplicatePreview: (Duplicate.Cluster, Duplicate) -> Unit = { _, _ -> },
    ) {
        setContent {
            PreviewWrapper {
                DeduplicatorListScreen(
                    stateSource = MutableStateFlow(state),
                    onClusterDelete = onClusterDelete,
                    onClusterClick = onClusterClick,
                    onClusterPreview = onClusterPreview,
                    onDuplicateClick = onDuplicateClick,
                    onDuplicatePreview = onDuplicatePreview,
                )
            }
        }
    }

    private fun gridState() =
        DeduplicatorListViewModel.State(rows = listOf(clusterRow("vacation")), layoutMode = LayoutMode.GRID)

    private fun linearState() =
        DeduplicatorListViewModel.State(rows = listOf(clusterRow("vacation")), layoutMode = LayoutMode.LINEAR)

    @Test
    fun `GRID thumbnail tap requests cluster deletion (not details or preview)`() {
        var delete = 0
        var details = 0
        var preview = 0
        composeRule.setScreen(
            gridState(),
            onClusterDelete = { delete++ },
            onClusterClick = { details++ },
            onClusterPreview = { preview++ },
        )
        // The details caption is overlaid on the bottom of the (square) thumbnail, so tap near the
        // top of the thumbnail — clearly above the caption — to exercise the delete region. A
        // center tap would land on the caption overlay on smaller tiles.
        composeRule.onNode(hasClickLabel("Delete duplicate set")).performTouchInput { click(topCenter) }
        composeRule.runOnIdle {
            if (delete != 1 || details != 0 || preview != 0) {
                throw AssertionError("delete=$delete details=$details preview=$preview")
            }
        }
    }

    @Test
    fun `GRID caption tap opens details`() {
        var delete = 0
        var details = 0
        composeRule.setScreen(gridState(), onClusterDelete = { delete++ }, onClusterClick = { details++ })
        composeRule.onNode(hasClickLabel("Show details")).performClick()
        composeRule.runOnIdle {
            if (details != 1 || delete != 0) throw AssertionError("details=$details delete=$delete")
        }
    }

    @Test
    fun `GRID preview button opens the full-screen preview`() {
        var preview = 0
        var delete = 0
        composeRule.setScreen(gridState(), onClusterPreview = { preview++ }, onClusterDelete = { delete++ })
        composeRule.onNode(hasClickLabel("Open preview")).performClick()
        composeRule.runOnIdle {
            if (preview != 1 || delete != 0) throw AssertionError("preview=$preview delete=$delete")
        }
    }

    @Test
    fun `GRID primary line shows the freeable-size label`() {
        composeRule.setScreen(gridState())
        composeRule.onNodeWithText("freeable", substring = true).assertExists()
    }

    @Test
    fun `LINEAR header tap requests cluster deletion`() {
        var delete = 0
        var details = 0
        composeRule.setScreen(linearState(), onClusterDelete = { delete++ }, onClusterClick = { details++ })
        composeRule.onNode(hasClickLabel("Delete duplicate set")).performClick()
        composeRule.runOnIdle {
            if (delete != 1 || details != 0) throw AssertionError("delete=$delete details=$details")
        }
    }

    @Test
    fun `LINEAR sub-row tap requests single-duplicate deletion`() {
        var dupeClicks = 0
        composeRule.setScreen(linearState(), onDuplicateClick = { _, _ -> dupeClicks++ })
        composeRule.onNodeWithText("vacation-a.jpg").performClick()
        composeRule.runOnIdle {
            if (dupeClicks != 1) throw AssertionError("dupeClicks=$dupeClicks")
        }
    }

    @Test
    fun `LINEAR header thumbnail opens the full-screen preview`() {
        var preview = 0
        var delete = 0
        composeRule.setScreen(linearState(), onClusterPreview = { preview++ }, onClusterDelete = { delete++ })
        composeRule.onNode(hasClickLabel("Open preview")).performClick()
        composeRule.runOnIdle {
            if (preview != 1 || delete != 0) throw AssertionError("preview=$preview delete=$delete")
        }
    }

    @Test
    fun `GRID long-press enters selection mode and suppresses the delete action`() {
        var delete = 0
        composeRule.setScreen(gridState(), onClusterDelete = { delete++ })
        // Long-press selects rather than deletes; the normal top bar (and its layout toggle) is
        // replaced by the selection top bar.
        composeRule.onNode(hasClickLabel("Delete duplicate set")).performTouchInput { longClick() }
        composeRule.runOnIdle {
            if (delete != 0) throw AssertionError("long-press must not delete, got delete=$delete")
        }
        composeRule.onNodeWithContentDescription("Switch view mode").assertDoesNotExist()
    }

    @Test
    fun `GRID preview button toggles selection rather than previewing while selecting`() {
        var preview = 0
        composeRule.setScreen(gridState(), onClusterPreview = { preview++ })
        // Long-press selects the cluster; the preview button must then behave like the other
        // regions (toggle) instead of opening the preview.
        composeRule.onNode(hasClickLabel("Delete duplicate set")).performTouchInput { longClick() }
        composeRule.onNode(hasClickLabel("Open preview")).performClick()
        composeRule.runOnIdle {
            if (preview != 0) throw AssertionError("preview must not fire during selection, got preview=$preview")
        }
    }

    // ─────────────────────────── keeper marker ───────────────────────────

    private fun keeperGridState() = DeduplicatorListViewModel.State(
        rows = listOf(clusterRow("vacation", withKeeper = true)),
        layoutMode = LayoutMode.GRID,
    )

    @Test
    fun `GRID tile shows the Kept badge for a row with a keeper`() {
        composeRule.setScreen(keeperGridState())
        composeRule.onNodeWithText("Kept").assertExists()
    }

    @Test
    fun `GRID tile shows no Kept badge for a row without a keeper`() {
        composeRule.setScreen(gridState())
        composeRule.onAllNodesWithText("Kept").assertCountEquals(0)
    }

    @Test
    fun `GRID tile previews the keeper's file`() {
        // FilePreviewImage only consults LocalPreviewImageProvider under inspection mode, so the
        // recorder sees exactly the lookup the tile asked for a bitmap of.
        val requested = mutableListOf<String>()
        val recorder = object : PreviewImageProvider {
            @Composable
            override fun fileImage(lookup: APathLookup<*>): Painter? {
                requested.add(lookup.path)
                return null
            }

            @Composable
            override fun appIcon(pkg: Pkg): Painter? = null
        }
        val row = clusterRow("vacation", withKeeper = true)
        composeRule.setContent {
            PreviewWrapper {
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    LocalPreviewImageProvider provides recorder,
                ) {
                    DeduplicatorListScreen(
                        stateSource = MutableStateFlow(
                            DeduplicatorListViewModel.State(rows = listOf(row), layoutMode = LayoutMode.GRID),
                        ),
                    )
                }
            }
        }
        composeRule.runOnIdle {
            if (requested != listOf(row.keeper!!.lookup.path)) {
                throw AssertionError("expected the keeper's lookup, got $requested")
            }
        }
    }

    @Test
    fun `GRID preview button opens the preview on the keeper`() {
        val row = clusterRow("vacation", withKeeper = true)
        var clusterPreview = 0
        val dupePreviews = mutableListOf<Duplicate>()
        composeRule.setScreen(
            DeduplicatorListViewModel.State(rows = listOf(row), layoutMode = LayoutMode.GRID),
            onClusterPreview = { clusterPreview++ },
            onDuplicatePreview = { _, dupe -> dupePreviews.add(dupe) },
        )
        composeRule.onNode(hasClickLabel("Open preview")).performClick()
        composeRule.runOnIdle {
            if (clusterPreview != 0) throw AssertionError("clusterPreview=$clusterPreview")
            if (dupePreviews.map { it.identifier } != listOf(row.keeper!!.identifier)) {
                throw AssertionError("expected one preview of the keeper, got ${dupePreviews.map { it.identifier }}")
            }
        }
    }

    @Test
    fun `GRID preview button toggles selection rather than previewing while selecting, with a keeper`() {
        var clusterPreview = 0
        var dupePreview = 0
        composeRule.setScreen(
            keeperGridState(),
            onClusterPreview = { clusterPreview++ },
            onDuplicatePreview = { _, _ -> dupePreview++ },
        )
        composeRule.onNode(hasClickLabel("Delete duplicate set")).performTouchInput { longClick() }
        composeRule.onNode(hasClickLabel("Open preview")).performClick()
        composeRule.runOnIdle {
            if (clusterPreview != 0 || dupePreview != 0) {
                throw AssertionError("clusterPreview=$clusterPreview dupePreview=$dupePreview")
            }
        }
    }

    @Test
    fun `LINEAR layout shows no Kept badge`() {
        composeRule.setScreen(
            DeduplicatorListViewModel.State(
                rows = listOf(clusterRow("vacation", withKeeper = true)),
                layoutMode = LayoutMode.LINEAR,
            ),
        )
        composeRule.onAllNodesWithText("Kept").assertCountEquals(0)
    }
}
