package eu.darken.sdmse.common

import android.content.Context
import android.util.AttributeSet
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ui.layoutInflator
import eu.darken.sdmse.databinding.ViewMascotBinding
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import kotlin.math.abs


class MascotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val ui = ViewMascotBinding.inflate(layoutInflator, this)
    private val wiggleAnim = AnimationUtils.loadAnimation(context, R.anim.anim_wiggle)

    private val widthScale = 0.7f
    private val heightScale = 0.6f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val child = getChildAt(0)

        val scaledWidth = (child.measuredWidth * widthScale).toInt()
        val scaledHeight = (child.measuredHeight * heightScale).toInt()

        setMeasuredDimension(scaledWidth, scaledHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0)!!

        val height = bottom - top
        val width = right - left

        val childWidth = child.measuredWidth
        val childHeight = child.measuredHeight

        val childLeft = (width - childWidth) / 2
        val childTop = (height - childHeight) / 2

        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
    }

    override fun onFinishInflate() {
        ui.root.apply {
            var clickCount = 0
            setOnClickListener {
                clickCount++
                if (clickCount % 5 == 0) startAnimation(wiggleAnim)
            }
        }

        when {
            isNewYears() -> ui.mascotOverlay.apply {
                setImageResource(R.drawable.mascot_hat_newyears_crop)
                rotation = 30f
                val layoutParams = (layoutParams as ConstraintLayout.LayoutParams).apply {
                    matchConstraintPercentHeight = 0.38f
                    matchConstraintPercentWidth = 0.38f
                    horizontalBias = 0.769f
                    verticalBias = 0.18f
                }
                setLayoutParams(layoutParams)
                isVisible = true
            }

            isXmasSeason() -> ui.mascotOverlay.apply {
                setImageResource(R.drawable.mascot_hat_xmas_crop)
                rotation = 31f
                val layoutParams = (layoutParams as ConstraintLayout.LayoutParams).apply {
                    matchConstraintPercentHeight = 0.36f
                    matchConstraintPercentWidth = 0.36f
                    horizontalBias = 0.73f
                    verticalBias = 0.25f
                }
                setLayoutParams(layoutParams)
                isVisible = true
            }

            else -> {
                ui.mascotOverlay.setImageDrawable(null)
                ui.mascotOverlay.isVisible = false
            }
        }

        super.onFinishInflate()
    }

    private fun isXmasSeason(): Boolean {
        val now = LocalDate.now()

        val start = LocalDate.of(now.year, Month.DECEMBER, 23)
        val end = LocalDate.of(now.year, Month.DECEMBER, 29)

        return now.isEqual(start) || now.isEqual(end) || (now.isAfter(start) && now.isBefore(end))
    }

    private fun isNewYears(): Boolean {
        val now = LocalDate.now()

        val newYearsEveThisYear = LocalDate.of(now.year, 12, 31)
        val newYearsEveLastYear = LocalDate.of(now.year - 1, 12, 31)

        val daysDifferenceThisYear = abs(ChronoUnit.DAYS.between(now, newYearsEveThisYear))
        val daysDifferenceLastYear = abs(ChronoUnit.DAYS.between(now, newYearsEveLastYear))

        val scope = 2

        return daysDifferenceThisYear <= scope || daysDifferenceLastYear <= scope
    }

    val isAnimating: Boolean
        get() = ui.mascotAnimated.isAnimating

    fun playAnimation() {
        ui.mascotAnimated.playAnimation()
    }

    fun pauseAnimation() {
        ui.mascotAnimated.pauseAnimation()
    }
}