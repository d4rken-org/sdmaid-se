package eu.darken.sdmse.swiper.ui.swipe

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SwiperSwipeFragmentBinding
import eu.darken.sdmse.common.previews.PreviewFragmentArgs
import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

@AndroidEntryPoint
class SwiperSwipeFragment : Fragment3(R.layout.swiper_swipe_fragment) {

    override val vm: SwiperSwipeViewModel by viewModels()
    override val ui: SwiperSwipeFragmentBinding by viewBinding()

    private lateinit var progressAdapter: ProgressPagerAdapter
    private var currentItems: List<SwipeItem> = emptyList()
    private var currentIndex: Int = 0
    private var isInitialScroll = true
    private var currentSwapDirections: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbar, top = true)
            insetsPadding(ui.actionContainer, bottom = true)
        }

        // Manual navigation setup to preserve XML navigation icon (close/X instead of back arrow)
        ui.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        // actionLayout requires manual click handling
        ui.toolbar.menu.findItem(R.id.action_review)?.actionView?.setOnClickListener {
            vm.navigateToStatus()
        }

        ui.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_help -> {
                    showHelpDialog()
                    true
                }

                else -> false
            }
        }

        // SwipeCardView setup
        ui.swipeCard.swipeListener = object : SwipeCardView.SwipeListener {
            override fun onSwipeLeft() {
                currentItems.getOrNull(currentIndex)?.let { item ->
                    vm.setDecision(item.id, SwipeDecision.DELETE)
                }
            }

            override fun onSwipeRight() {
                currentItems.getOrNull(currentIndex)?.let { item ->
                    vm.setDecision(item.id, SwipeDecision.KEEP)
                }
            }

            override fun onSwipeUp() {
                vm.skip()
            }

            override fun onSwipeDown() {
                vm.undo()
            }

            override fun onSwipeProgress(progress: Float, direction: SwipeCardView.SwipeDirection?) {
                // Could add visual feedback here if needed
            }

            override fun onPreviewClick(item: SwipeItem) {
                val options = PreviewOptions(
                    paths = listOf(item.lookup.lookedUp),
                    position = 0,
                )
                findNavController().navigate(
                    resId = R.id.goToPreview,
                    args = PreviewFragmentArgs(options = options).toBundle(),
                )
            }
        }

        // Progress pager setup
        progressAdapter = ProgressPagerAdapter()
        ui.progressPager.adapter = progressAdapter
        ui.progressPager.itemAnimator = null
        progressAdapter.onItemClick = { position ->
            vm.setCurrentIndex(position)
        }

        // Add center-scaling item decoration
        ui.progressPager.addItemDecoration(CenterScaleItemDecoration())

        // Add scroll listener to trigger re-decoration on scroll
        ui.progressPager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                recyclerView.invalidate()
            }
        })

        // FAB click handlers with scale animation
        ui.undoAction.setOnClickListener { view ->
            animateFabPress(view)
            vm.undo()
        }
        ui.skipAction.setOnClickListener { view ->
            animateFabPress(view)
            vm.skip()
        }
        ui.skipAction.setOnLongClickListener {
            currentItems.getOrNull(currentIndex)?.let { item ->
                showExcludeDialog(item)
            }
            true
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                SwiperSwipeEvents.NavigateToSessions -> {
                    findNavController().popBackStack(R.id.swiperSessionsFragment, inclusive = false)
                }

                SwiperSwipeEvents.TriggerHapticFeedback -> {
                    ui.root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            }
        }

        vm.state.observe2(ui) { state: SwiperSwipeViewModel.State ->
            // Update button actions and icons based on swapDirections setting
            if (state.swapDirections) {
                // Swapped: left = keep, right = delete
                deleteAction.setOnClickListener { view ->
                    animateFabPress(view)
                    currentItems.getOrNull(currentIndex)?.let { item ->
                        vm.setDecision(item.id, SwipeDecision.KEEP)
                    }
                }
                keepAction.setOnClickListener { view ->
                    animateFabPress(view)
                    currentItems.getOrNull(currentIndex)?.let { item ->
                        vm.setDecision(item.id, SwipeDecision.DELETE)
                    }
                }
                // Swap icons
                deleteAction.setImageResource(R.drawable.ic_heart)
                keepAction.setImageResource(R.drawable.ic_delete)
                // Swap labels
                deleteLabel.setText(R.string.swiper_keep_action)
                keepLabel.setText(eu.darken.sdmse.common.R.string.general_delete_action)
                // Swap tints: left button gets primary (keep), right button gets error (delete)
                deleteAction.backgroundTintList = ColorStateList.valueOf(
                    requireContext().getColorForAttr(com.google.android.material.R.attr.colorPrimaryContainer),
                )
                deleteAction.imageTintList = ColorStateList.valueOf(
                    requireContext().getColorForAttr(com.google.android.material.R.attr.colorOnPrimaryContainer),
                )
                keepAction.backgroundTintList = ColorStateList.valueOf(
                    requireContext().getColorForAttr(com.google.android.material.R.attr.colorErrorContainer),
                )
                keepAction.imageTintList = ColorStateList.valueOf(
                    requireContext().getColorForAttr(com.google.android.material.R.attr.colorOnErrorContainer),
                )
            } else {
                // Normal: left = delete, right = keep
                deleteAction.setOnClickListener { view ->
                    animateFabPress(view)
                    currentItems.getOrNull(currentIndex)?.let { item ->
                        vm.setDecision(item.id, SwipeDecision.DELETE)
                    }
                }
                keepAction.setOnClickListener { view ->
                    animateFabPress(view)
                    currentItems.getOrNull(currentIndex)?.let { item ->
                        vm.setDecision(item.id, SwipeDecision.KEEP)
                    }
                }
                // Normal icons
                deleteAction.setImageResource(R.drawable.ic_delete)
                keepAction.setImageResource(R.drawable.ic_heart)
                // Normal labels
                deleteLabel.setText(eu.darken.sdmse.common.R.string.general_delete_action)
                keepLabel.setText(R.string.swiper_keep_action)
                // Normal tints: left button gets error (delete), right button gets primary (keep)
                deleteAction.backgroundTintList = ColorStateList.valueOf(
                    requireContext().getColorForAttr(com.google.android.material.R.attr.colorErrorContainer),
                )
                deleteAction.imageTintList = ColorStateList.valueOf(
                    requireContext().getColorForAttr(com.google.android.material.R.attr.colorOnErrorContainer),
                )
                keepAction.backgroundTintList = ColorStateList.valueOf(
                    requireContext().getColorForAttr(com.google.android.material.R.attr.colorPrimaryContainer),
                )
                keepAction.imageTintList = ColorStateList.valueOf(
                    requireContext().getColorForAttr(com.google.android.material.R.attr.colorOnPrimaryContainer),
                )
            }
            currentItems = state.items
            currentIndex = state.currentIndex
            currentSwapDirections = state.swapDirections

            progressAdapter.submitList(state.items)
            progressAdapter.currentItemIndex = state.currentIndex

            // Update SwipeCardView with file info provider
            swipeCard.swapDirections = state.swapDirections
            swipeCard.canUndo = state.canUndo
            val nextItem = state.items.getOrNull(state.currentIndex + 1)
            swipeCard.bind(
                currentItem = state.currentItem,
                nextItem = nextItem,
                showDetails = state.showDetails,
                fileInfoProvider = { item ->
                    val itemFileName = item.lookup.name
                    val fullPath = item.lookup.userReadablePath.get(requireContext())
                    val (size, _) = ByteFormatter.formatSize(requireContext(), item.lookup.size)
                    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    val date = item.lookup.modifiedAt.atZone(ZoneId.systemDefault()).format(dateFormatter)
                    SwipeCardView.FileInfo(
                        fileName = itemFileName,
                        filePath = fullPath.removeSuffix(itemFileName),
                        fileMeta = "$size • $date",
                        position = state.currentItemOriginalIndex?.let { idx ->
                            getString(
                                R.string.swiper_item_position,
                                idx + 1,
                                state.totalItems,
                            )
                        },
                    )
                },
            )

            // Center current item in RecyclerView
            if (isInitialScroll) {
                scrollToCenterInstant(progressPager, state.currentIndex)
                isInitialScroll = false
            } else {
                smoothScrollToCenter(progressPager, state.currentIndex)
            }

            toolbar.subtitle = state.sessionLabel
                ?: state.sessionPosition?.let { getString(R.string.swiper_session_default_label, it) }

            progressBar.progress = state.progressPercent
            progressPercent.text = "${state.progressPercent}%"

            // Simplified count display - icons always match their semantic meaning
            keepCount.text = "${state.keepCount}"
            deleteCount.text = "${state.deleteCount}"
            undecidedCount.text = "${state.undecidedCount}"

            // Animate undo container visibility (FAB + label)
            val undoVisible = state.canUndo
            if (undoVisible && undoContainer.visibility != View.VISIBLE) {
                undoContainer.visibility = View.VISIBLE
                undoContainer.alpha = 0f
                undoContainer.scaleX = 0.5f
                undoContainer.scaleY = 0.5f
                undoContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
            } else if (!undoVisible && undoContainer.visibility == View.VISIBLE) {
                undoContainer.animate()
                    .alpha(0f)
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .setDuration(150)
                    .withEndAction { undoContainer.visibility = View.INVISIBLE }
                    .start()
            }

            // Gesture overlay (first-use onboarding)
            if (state.showGestureOverlay) {
                gestureOverlay.root.visibility = View.VISIBLE
                if (state.swapDirections) {
                    gestureOverlay.overlayLeftIcon.setImageResource(R.drawable.ic_heart)
                    gestureOverlay.overlayLeftLabel.setText(R.string.swiper_gesture_overlay_left_keep)
                    gestureOverlay.overlayRightIcon.setImageResource(R.drawable.ic_delete)
                    gestureOverlay.overlayRightLabel.setText(R.string.swiper_gesture_overlay_right_delete)
                } else {
                    gestureOverlay.overlayLeftIcon.setImageResource(R.drawable.ic_delete)
                    gestureOverlay.overlayLeftLabel.setText(R.string.swiper_gesture_overlay_left_delete)
                    gestureOverlay.overlayRightIcon.setImageResource(R.drawable.ic_heart)
                    gestureOverlay.overlayRightLabel.setText(R.string.swiper_gesture_overlay_right_keep)
                }
                val dismissOverlay = View.OnClickListener {
                    gestureOverlay.root.visibility = View.GONE
                    vm.dismissGestureOverlay()
                }
                gestureOverlay.root.setOnClickListener(dismissOverlay)
                gestureOverlay.overlayDismissAction.setOnClickListener(dismissOverlay)
            } else {
                gestureOverlay.root.visibility = View.GONE
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    private fun showHelpDialog() {
        val leftAction = if (currentSwapDirections) {
            getString(R.string.swiper_keep_action)
        } else {
            getString(eu.darken.sdmse.common.R.string.general_delete_action)
        }
        val rightAction = if (currentSwapDirections) {
            getString(eu.darken.sdmse.common.R.string.general_delete_action)
        } else {
            getString(R.string.swiper_keep_action)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.swiper_help_title)
            .setMessage(getString(R.string.swiper_help_message, leftAction, rightAction))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showExcludeDialog(item: SwipeItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.exclusion_create_action)
            .setMessage(getString(R.string.swiper_exclude_confirmation_message, item.lookup.userReadablePath.get(requireContext())))
            .setPositiveButton(eu.darken.sdmse.common.R.string.general_exclude_action) { _, _ ->
                vm.excludeAndRemove(item)
            }
            .setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action, null)
            .show()
    }

    private fun animateFabPress(view: View) {
        view.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(100)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
            .start()
    }

    private fun smoothScrollToCenter(recyclerView: RecyclerView, position: Int) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return

        val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
            override fun calculateDtToFit(
                viewStart: Int,
                viewEnd: Int,
                boxStart: Int,
                boxEnd: Int,
                snapPreference: Int,
            ): Int {
                val viewCenter = (viewStart + viewEnd) / 2
                val boxCenter = (boxStart + boxEnd) / 2
                return boxCenter - viewCenter
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                return 50f / displayMetrics.densityDpi
            }
        }

        smoothScroller.targetPosition = position
        layoutManager.startSmoothScroll(smoothScroller)
    }

    private fun scrollToCenterInstant(recyclerView: RecyclerView, position: Int) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        // Post to ensure RecyclerView is laid out before calculating offset
        recyclerView.post {
            val childWidth = recyclerView.getChildAt(0)?.width ?: return@post
            val offset = (recyclerView.width - childWidth) / 2
            layoutManager.scrollToPositionWithOffset(position, offset)
        }
    }

    /**
     * ItemDecoration that scales items based on their distance from the center of the RecyclerView.
     * Items closer to the center appear larger and more prominent.
     */
    private class CenterScaleItemDecoration : RecyclerView.ItemDecoration() {
        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val centerX = parent.width / 2f
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val childCenterX = (child.left + child.right) / 2f
                val distance = abs(childCenterX - centerX)
                val maxDistance = parent.width / 2f
                val normalizedDistance = (distance / maxDistance).coerceIn(0f, 1f)

                // Scale from 1.0 (center) to 0.85 (edge)
                val scale = 1f - normalizedDistance * 0.15f

                // Alpha from 1.0 (center) to 0.6 (edge)
                val alpha = 1f - normalizedDistance * 0.4f

                child.scaleX = scale.coerceIn(0.85f, 1f)
                child.scaleY = scale.coerceIn(0.85f, 1f)
                child.alpha = alpha.coerceIn(0.6f, 1f)
            }
        }
    }
}
