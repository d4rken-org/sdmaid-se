package eu.darken.sdmse.common.lists

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

class ViewHolderBasedDivider constructor(
    context: Context,
    private val filter: (RecyclerView.ViewHolder?, RecyclerView.ViewHolder, RecyclerView.ViewHolder?) -> Boolean,
) : RecyclerView.ItemDecoration() {

    private var divider: Drawable
    private val bounds: Rect = Rect()

    init {
        val attr = context.obtainStyledAttributes(ATTRS)
        divider = attr.getDrawable(0)!!
        attr.recycle()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.layoutManager == null) return

        drawVertical(c, parent)
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
        val childCount = parent.childCount - 1
        for (i in 0 until childCount) {
            val previousChild: View? = parent.getChildAt(i - 1)
            val thisChild: View = parent.getChildAt(i)
            val nextChild: View? = parent.getChildAt(i + 1)

            val previousVH = previousChild?.let { parent.findContainingViewHolder(it) }
            val thisVH = parent.findContainingViewHolder(thisChild)!!
            val nextVH = nextChild?.let { parent.findContainingViewHolder(it) }

            if (!filter(previousVH, thisVH, nextVH)) continue

            parent.getDecoratedBoundsWithMargins(thisChild, bounds)
            val bottom: Int = bounds.bottom + ViewCompat.getTranslationY(thisChild).roundToInt()
            val top: Int = bottom - divider.intrinsicHeight
            divider.setBounds(left, top, right, bottom)
            divider.draw(canvas)
        }
        canvas.restore()
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.set(0, 0, 0, divider.intrinsicHeight)
    }

    companion object {
        private val ATTRS = intArrayOf(android.R.attr.listDivider)
    }
}