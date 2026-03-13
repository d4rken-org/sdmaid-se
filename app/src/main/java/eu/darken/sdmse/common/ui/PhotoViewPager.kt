package eu.darken.sdmse.common.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.ViewPager
import com.github.chrisbanes.photoview.PhotoView

/**
 * ViewPager that respects PhotoView zoom state:
 * - Zoomed out: normal paging with horizontal swipes
 * - Zoomed in: let PhotoView handle panning
 */
class PhotoViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewPager(context, attrs) {

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return try {
            // Don't intercept if PhotoView is zoomed in
            if (isCurrentPhotoViewZoomed()) {
                false
            } else {
                super.onInterceptTouchEvent(ev)
            }
        } catch (e: IllegalArgumentException) {
            // PhotoView can throw this during certain touch sequences
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

    private fun isCurrentPhotoViewZoomed(): Boolean {
        val currentView = findViewByPosition(currentItem) ?: return false
        val photoView = findPhotoView(currentView) ?: return false
        return photoView.scale > 1.05f // Small threshold to account for floating point
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

    private fun findPhotoView(view: View): PhotoView? {
        if (view is PhotoView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findPhotoView(view.getChildAt(i))
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
