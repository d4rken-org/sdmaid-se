package eu.darken.sdmse.swiper.ui.swipe

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.FrameLayout
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.databinding.SwiperCardItemBinding
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import kotlin.math.abs

class SwipeCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class SwipeDirection {
        LEFT, RIGHT, UP, DOWN
    }

    interface SwipeListener {
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onSwipeUp()
        fun onSwipeDown()
        fun onSwipeProgress(progress: Float, direction: SwipeDirection?)
    }

    private val binding: SwiperCardItemBinding = SwiperCardItemBinding.inflate(LayoutInflater.from(context), this, true)

    var swipeListener: SwipeListener? = null
    var swapDirections: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateStampPositions()
            }
        }
    var canUndo: Boolean = false

    private var initialX = 0f
    private var initialY = 0f
    private var isDragging = false
    private var velocityTracker: VelocityTracker? = null

    private val swipeThreshold: Float
        get() = width * 0.4f

    private val velocityThreshold = 1000f // pixels per second

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            return true
        }
    })

    // Stamp text resource IDs
    private val keepStampResources = listOf(
        R.string.swiper_stamp_keep_1,
        R.string.swiper_stamp_keep_2,
        R.string.swiper_stamp_keep_3,
        R.string.swiper_stamp_keep_4,
    )
    private val deleteStampResources = listOf(
        R.string.swiper_stamp_delete_1,
        R.string.swiper_stamp_delete_2,
        R.string.swiper_stamp_delete_3,
        R.string.swiper_stamp_delete_4,
    )
    private val skipStampResources = listOf(
        R.string.swiper_stamp_skip_1,
        R.string.swiper_stamp_skip_2,
        R.string.swiper_stamp_skip_3,
        R.string.swiper_stamp_skip_4,
    )
    private val undoStampResources = listOf(
        R.string.swiper_stamp_undo_1,
        R.string.swiper_stamp_undo_2,
        R.string.swiper_stamp_undo_3,
        R.string.swiper_stamp_undo_4,
    )

    init {
        clipChildren = false
        clipToPadding = false
    }

    private fun updateStampPositions() {
        val margin24dp = context.dpToPx(24f)

        val keepParams = binding.stampKeep.layoutParams as FrameLayout.LayoutParams
        val deleteParams = binding.stampDelete.layoutParams as FrameLayout.LayoutParams

        if (swapDirections) {
            // Swapped: Keep stamp on right (end), Delete stamp on left (start)
            keepParams.gravity = android.view.Gravity.TOP or android.view.Gravity.END
            keepParams.marginStart = 0
            keepParams.marginEnd = margin24dp
            binding.stampKeep.rotation = 15f

            deleteParams.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            deleteParams.marginStart = margin24dp
            deleteParams.marginEnd = 0
            binding.stampDelete.rotation = -15f
        } else {
            // Normal: Keep stamp on left (start), Delete stamp on right (end)
            keepParams.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            keepParams.marginStart = margin24dp
            keepParams.marginEnd = 0
            binding.stampKeep.rotation = -15f

            deleteParams.gravity = android.view.Gravity.TOP or android.view.Gravity.END
            deleteParams.marginStart = 0
            deleteParams.marginEnd = margin24dp
            binding.stampDelete.rotation = 15f
        }

        binding.stampKeep.layoutParams = keepParams
        binding.stampDelete.layoutParams = deleteParams
    }

    fun bind(
        currentItem: SwipeItem?,
        nextItem: SwipeItem? = null,
        showDetails: Boolean = false,
        fileInfoProvider: ((SwipeItem) -> FileInfo)? = null,
        nextItemPosition: Int? = null,
    ) {
        // Bind front card (current item)
        bindFrontCard(currentItem)

        // Bind back card (next item preview)
        bindBackCard(nextItem, nextItemPosition)

        // Bind file info overlay
        bindFileInfo(currentItem, showDetails, fileInfoProvider)

        // Reset position
        resetPosition()

        // Set random stamp texts for this card
        binding.stampKeepText.setText(keepStampResources.random())
        binding.stampDeleteText.setText(deleteStampResources.random())
        binding.stampSkipText.setText(skipStampResources.random())
        binding.stampUndoText.setText(undoStampResources.random())
    }

    data class FileInfo(
        val fileName: String,
        val filePath: String,
        val fileMeta: String,
        val position: String? = null,
    )

    private fun bindFileInfo(
        item: SwipeItem?,
        showDetails: Boolean,
        fileInfoProvider: ((SwipeItem) -> FileInfo)?,
    ) {
        binding.fileInfoOverlay.isVisible = showDetails && item != null
        if (item != null && showDetails && fileInfoProvider != null) {
            val info = fileInfoProvider(item)
            binding.fileName.text = info.fileName
            binding.filePath.text = info.filePath
            binding.fileMeta.text = info.fileMeta
            binding.filePosition.text = info.position
            binding.filePosition.isVisible = info.position != null
        }
    }

    private fun bindFrontCard(item: SwipeItem?) {
        if (item == null) {
            binding.previewImage.isVisible = false
            binding.noPreviewContainer.isVisible = true
            return
        }

        binding.previewImage.loadFilePreview(item.lookup) {
            listener(
                onSuccess = { _, _ ->
                    binding.previewImage.isVisible = true
                    binding.noPreviewContainer.isVisible = false
                },
                onError = { _, _ ->
                    binding.previewImage.isVisible = false
                    binding.noPreviewContainer.isVisible = true
                },
            )
        }

        // Show a subtle indicator for already-decided items
        when (item.decision) {
            SwipeDecision.KEEP -> {
                binding.stampKeep.alpha = 0.3f
                binding.stampKeep.scaleX = 1f
                binding.stampKeep.scaleY = 1f
                binding.stampDelete.alpha = 0f
                binding.stampSkip.alpha = 0f
                binding.stampUndo.alpha = 0f
            }
            SwipeDecision.DELETE -> {
                binding.stampDelete.alpha = 0.3f
                binding.stampDelete.scaleX = 1f
                binding.stampDelete.scaleY = 1f
                binding.stampKeep.alpha = 0f
                binding.stampSkip.alpha = 0f
                binding.stampUndo.alpha = 0f
            }
            else -> {
                binding.stampKeep.alpha = 0f
                binding.stampDelete.alpha = 0f
                binding.stampSkip.alpha = 0f
                binding.stampUndo.alpha = 0f
            }
        }
    }

    private fun bindBackCard(item: SwipeItem?, position: Int?) {
        if (item == null) {
            binding.backCard.isVisible = false
            return
        }

        binding.backCard.isVisible = true

        binding.backPreviewImage.loadFilePreview(item.lookup) {
            listener(
                onSuccess = { _, _ ->
                    binding.backPreviewImage.isVisible = true
                    binding.backNoPreviewContainer.isVisible = false
                },
                onError = { _, _ ->
                    binding.backPreviewImage.isVisible = false
                    binding.backNoPreviewContainer.isVisible = true
                },
            )
        }

        // Set queue position badge
        if (position != null) {
            binding.backQueuePosition.isVisible = true
            binding.backQueuePosition.text = "#$position"
        } else {
            binding.backQueuePosition.isVisible = false
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.rawX
                initialY = event.rawY
                isDragging = true
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false

                velocityTracker?.addMovement(event)

                val deltaX = event.rawX - initialX
                val deltaY = event.rawY - initialY

                // Determine swipe direction based on movement
                val absX = abs(deltaX)
                val absY = abs(deltaY)

                // Allow upward swipes always, downward only when canUndo is true
                val effectiveY = when {
                    deltaY < 0 -> deltaY // Upward always allowed
                    canUndo -> deltaY // Downward only if canUndo
                    else -> 0f // Block downward when nothing to undo
                }

                // Apply translation
                binding.contentContainer.translationX = deltaX
                binding.contentContainer.translationY = effectiveY

                // Apply rotation based on horizontal movement
                val rotation = deltaX / width * 15f
                binding.contentContainer.rotation = rotation

                // Calculate progress and direction for feedback
                val progress = when {
                    absX > absY -> absX / swipeThreshold
                    absY > 0 -> absY / swipeThreshold
                    else -> 0f
                }

                val direction = when {
                    absX > absY && deltaX < 0 -> SwipeDirection.LEFT
                    absX > absY && deltaX > 0 -> SwipeDirection.RIGHT
                    deltaY < 0 -> SwipeDirection.UP
                    deltaY > 0 && canUndo -> SwipeDirection.DOWN
                    else -> null
                }

                // Update stamp visibility based on swipe direction
                val stampAlpha = (progress * 2.0f).coerceIn(0f, 1f)
                val stampScale = (0.5f + progress * 0.5f).coerceIn(0.5f, 1f)

                when (direction) {
                    SwipeDirection.LEFT -> {
                        val actualDirection = if (swapDirections) SwipeDirection.RIGHT else SwipeDirection.LEFT
                        if (actualDirection == SwipeDirection.LEFT) {
                            // Delete stamp
                            binding.stampDelete.alpha = stampAlpha
                            binding.stampDelete.scaleX = stampScale
                            binding.stampDelete.scaleY = stampScale
                            binding.stampKeep.alpha = 0f
                            binding.stampSkip.alpha = 0f
                            binding.stampUndo.alpha = 0f
                        } else {
                            // Keep stamp
                            binding.stampKeep.alpha = stampAlpha
                            binding.stampKeep.scaleX = stampScale
                            binding.stampKeep.scaleY = stampScale
                            binding.stampDelete.alpha = 0f
                            binding.stampSkip.alpha = 0f
                            binding.stampUndo.alpha = 0f
                        }
                    }
                    SwipeDirection.RIGHT -> {
                        val actualDirection = if (swapDirections) SwipeDirection.LEFT else SwipeDirection.RIGHT
                        if (actualDirection == SwipeDirection.RIGHT) {
                            // Keep stamp
                            binding.stampKeep.alpha = stampAlpha
                            binding.stampKeep.scaleX = stampScale
                            binding.stampKeep.scaleY = stampScale
                            binding.stampDelete.alpha = 0f
                            binding.stampSkip.alpha = 0f
                            binding.stampUndo.alpha = 0f
                        } else {
                            // Delete stamp
                            binding.stampDelete.alpha = stampAlpha
                            binding.stampDelete.scaleX = stampScale
                            binding.stampDelete.scaleY = stampScale
                            binding.stampKeep.alpha = 0f
                            binding.stampSkip.alpha = 0f
                            binding.stampUndo.alpha = 0f
                        }
                    }
                    SwipeDirection.UP -> {
                        // Skip stamp
                        binding.stampSkip.alpha = stampAlpha
                        binding.stampSkip.scaleX = stampScale
                        binding.stampSkip.scaleY = stampScale
                        binding.stampKeep.alpha = 0f
                        binding.stampDelete.alpha = 0f
                        binding.stampUndo.alpha = 0f
                    }
                    SwipeDirection.DOWN -> {
                        // Undo stamp
                        binding.stampUndo.alpha = stampAlpha
                        binding.stampUndo.scaleX = stampScale
                        binding.stampUndo.scaleY = stampScale
                        binding.stampKeep.alpha = 0f
                        binding.stampDelete.alpha = 0f
                        binding.stampSkip.alpha = 0f
                    }
                    null -> {
                        binding.stampKeep.alpha = 0f
                        binding.stampDelete.alpha = 0f
                        binding.stampSkip.alpha = 0f
                        binding.stampUndo.alpha = 0f
                    }
                }

                swipeListener?.onSwipeProgress(progress.coerceAtMost(1f), direction)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false

                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)

                velocityTracker?.computeCurrentVelocity(1000)
                val velocityX = velocityTracker?.xVelocity ?: 0f
                val velocityY = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                val deltaX = binding.contentContainer.translationX
                val deltaY = binding.contentContainer.translationY

                val absX = abs(deltaX)
                val absY = abs(deltaY)
                val absVelocityX = abs(velocityX)
                val absVelocityY = abs(velocityY)

                // Determine if swipe threshold was met
                when {
                    // Horizontal swipe takes priority
                    (absX > swipeThreshold || absVelocityX > velocityThreshold) && absX > absY -> {
                        val direction = if (deltaX > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                        animateSwipeOff(direction) {
                            val actualDirection = if (swapDirections) {
                                if (direction == SwipeDirection.LEFT) SwipeDirection.RIGHT else SwipeDirection.LEFT
                            } else {
                                direction
                            }
                            when (actualDirection) {
                                SwipeDirection.LEFT -> swipeListener?.onSwipeLeft()
                                SwipeDirection.RIGHT -> swipeListener?.onSwipeRight()
                                SwipeDirection.UP -> swipeListener?.onSwipeUp()
                                SwipeDirection.DOWN -> swipeListener?.onSwipeDown()
                            }
                        }
                    }
                    // Upward swipe
                    deltaY < 0 && (absY > swipeThreshold || absVelocityY > velocityThreshold) -> {
                        animateSwipeOff(SwipeDirection.UP) {
                            swipeListener?.onSwipeUp()
                        }
                    }
                    // Downward swipe (only when canUndo)
                    canUndo && deltaY > 0 && (absY > swipeThreshold || absVelocityY > velocityThreshold) -> {
                        animateSwipeOff(SwipeDirection.DOWN) {
                            swipeListener?.onSwipeDown()
                        }
                    }
                    else -> {
                        // Snap back
                        animateReset()
                    }
                }
                return true
            }
        }
        return false
    }

    private fun animateSwipeOff(direction: SwipeDirection, onComplete: () -> Unit) {
        val (targetX, targetY, targetRotation) = when (direction) {
            SwipeDirection.LEFT -> Triple(-width * 2f, 0f, -30f)
            SwipeDirection.RIGHT -> Triple(width * 2f, 0f, 30f)
            SwipeDirection.UP -> Triple(0f, -height * 2f, 0f)
            SwipeDirection.DOWN -> Triple(0f, height * 2f, 0f)
        }

        binding.contentContainer.animate()
            .translationX(targetX)
            .translationY(targetY)
            .rotation(targetRotation)
            .alpha(0f)
            .setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Reset position but keep alpha at 0 - bind() will restore visibility
                    binding.contentContainer.translationX = 0f
                    binding.contentContainer.translationY = 0f
                    binding.contentContainer.rotation = 0f
                    // Alpha stays at 0 until bind() calls resetPosition()
                    binding.stampKeep.alpha = 0f
                    binding.stampDelete.alpha = 0f
                    binding.stampSkip.alpha = 0f
                    binding.stampUndo.alpha = 0f
                    onComplete()
                }
            })
            .start()
    }

    private fun animateReset() {
        binding.contentContainer.animate()
            .translationX(0f)
            .translationY(0f)
            .rotation(0f)
            .alpha(1f)
            .setDuration(200)
            .setListener(null)
            .start()

        binding.stampKeep.animate()
            .alpha(0f)
            .scaleX(0.5f)
            .scaleY(0.5f)
            .setDuration(200)
            .start()

        binding.stampDelete.animate()
            .alpha(0f)
            .scaleX(0.5f)
            .scaleY(0.5f)
            .setDuration(200)
            .start()

        binding.stampSkip.animate()
            .alpha(0f)
            .scaleX(0.5f)
            .scaleY(0.5f)
            .setDuration(200)
            .start()

        binding.stampUndo.animate()
            .alpha(0f)
            .scaleX(0.5f)
            .scaleY(0.5f)
            .setDuration(200)
            .start()

        swipeListener?.onSwipeProgress(0f, null)
    }

    fun resetPosition() {
        binding.contentContainer.translationX = 0f
        binding.contentContainer.translationY = 0f
        binding.contentContainer.rotation = 0f
        binding.contentContainer.alpha = 1f
        binding.stampKeep.alpha = 0f
        binding.stampKeep.scaleX = 0.5f
        binding.stampKeep.scaleY = 0.5f
        binding.stampDelete.alpha = 0f
        binding.stampDelete.scaleX = 0.5f
        binding.stampDelete.scaleY = 0.5f
        binding.stampSkip.alpha = 0f
        binding.stampSkip.scaleX = 0.5f
        binding.stampSkip.scaleY = 0.5f
        binding.stampUndo.alpha = 0f
        binding.stampUndo.scaleX = 0.5f
        binding.stampUndo.scaleY = 0.5f
    }

}
