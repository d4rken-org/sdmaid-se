package eu.darken.sdmse.common.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.ViewPager
import com.github.panpf.zoomimage.ZoomImageView

/**
 * ViewPager that respects ZoomImageView zoom state:
 * - Zoomed out: normal paging with horizontal swipes
 * - Zoomed in: let the image view handle panning
 */
class ZoomAwareViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewPager(context, attrs) {

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return try {
            if (isCurrentImageZoomed()) {
                false
            } else {
                super.onInterceptTouchEvent(ev)
            }
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun isCurrentImageZoomed(): Boolean {
        val currentView = findViewByPosition(currentItem) ?: return false
        val zoomView = findZoomImageView(currentView) ?: return false
        val zoomable = zoomView.zoomable
        val currentScale = zoomable.transformState.value.scaleX
        val minScale = zoomable.minScaleState.value
        return currentScale > minScale * 1.05f
    }

    private fun findViewByPosition(position: Int): View? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val layoutParams = child.layoutParams as? LayoutParams
            if (layoutParams != null && !layoutParams.isDecor && layoutParams.position == position) {
                return child
            }
        }
        return null
    }

    private fun findZoomImageView(view: View): ZoomImageView? {
        if (view is ZoomImageView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findZoomImageView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private val LayoutParams.position: Int
        get() = try {
            val field = LayoutParams::class.java.getDeclaredField("position")
            field.isAccessible = true
            field.getInt(this)
        } catch (e: Exception) {
            -1
        }
}
