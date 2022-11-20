package eu.darken.sdmse.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Like DividerItemDecoration but no divider for the last element
 * Creates a divider [RecyclerView.ItemDecoration] that can be used with a
 * [LinearLayoutManager].
 *
 * @param context Current context, it will be used to access resources.
 * @param orientation Divider orientation. Should be [.HORIZONTAL] or [.VERTICAL].
 */
class DividerItemDecoration2 constructor(
    context: Context,
    orientation: Int,
    private val drawAfterLastItem: Boolean = false,
) : RecyclerView.ItemDecoration() {

    private var divider: Drawable
    private var orientation = 0
    private val bounds: Rect = Rect()

    init {
        val attr = context.obtainStyledAttributes(ATTRS)
        divider = attr.getDrawable(0)!!
        attr.recycle()
        setOrientation(orientation)
    }

    /**
     * Sets the orientation for this divider. This should be called if
     * [RecyclerView.LayoutManager] changes orientation.
     *
     * @param orientation [.HORIZONTAL] or [.VERTICAL]
     */
    fun setOrientation(orientation: Int) {
        require(!(orientation != HORIZONTAL && orientation != VERTICAL)) {
            "Invalid orientation. It should be either HORIZONTAL or VERTICAL"
        }
        this.orientation = orientation
    }

    /**
     * Sets the [Drawable] for this divider.
     *
     * @param drawable Drawable that should be used as a divider.
     */
    fun setDrawable(drawable: Drawable) {
        divider = drawable
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.layoutManager == null) {
            return
        }
        if (orientation == VERTICAL) {
            drawVertical(c, parent)
        } else {
            drawHorizontal(c, parent)
        }
    }

    private fun drawVertical(canvas: Canvas, parent: RecyclerView) {
        canvas.save()
        val left: Int
        val right: Int
        if (parent.clipToPadding) {
            left = parent.paddingLeft
            right = parent.width - parent.paddingRight
            canvas.clipRect(
                left, parent.paddingTop, right,
                parent.height - parent.paddingBottom
            )
        } else {
            left = 0
            right = parent.width
        }
        val childCount = if (drawAfterLastItem) {
            parent.childCount
        } else {
            parent.childCount - 1
        }
        for (i in 0 until childCount) {
            val child: View = parent.getChildAt(i)
            parent.getDecoratedBoundsWithMargins(child, bounds)
            val bottom: Int = bounds.bottom + Math.round(ViewCompat.getTranslationY(child))
            val top: Int = bottom - divider.intrinsicHeight
            divider.setBounds(left, top, right, bottom)
            divider.draw(canvas)
        }
        canvas.restore()
    }

    private fun drawHorizontal(canvas: Canvas, parent: RecyclerView) {
        canvas.save()
        val top: Int
        val bottom: Int
        if (parent.clipToPadding) {
            top = parent.paddingTop
            bottom = parent.height - parent.paddingBottom
            canvas.clipRect(
                parent.paddingLeft, top,
                parent.width - parent.paddingRight, bottom
            )
        } else {
            top = 0
            bottom = parent.height
        }
        val childCount = if (drawAfterLastItem) {
            parent.childCount
        } else {
            parent.childCount - 1
        }
        for (i in 0 until childCount) {
            val child: View = parent.getChildAt(i)
            parent.layoutManager!!.getDecoratedBoundsWithMargins(child, bounds)
            val right: Int = bounds.right + Math.round(ViewCompat.getTranslationX(child))
            val left: Int = right - divider.intrinsicWidth
            divider.setBounds(left, top, right, bottom)
            divider.draw(canvas)
        }
        canvas.restore()
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (orientation == VERTICAL) {
            outRect.set(0, 0, 0, divider.intrinsicHeight)
        } else {
            outRect.set(0, 0, divider.intrinsicWidth, 0)
        }
    }

    companion object {
        const val HORIZONTAL = LinearLayout.HORIZONTAL
        const val VERTICAL = LinearLayout.VERTICAL
        private val ATTRS = intArrayOf(android.R.attr.listDivider)
    }
}