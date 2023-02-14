package eu.darken.sdmse.appcleaner.ui.details.appjunk

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileCategoryVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementHeaderVH
import kotlin.math.roundToInt

/**
 * Like DividerItemDecoration but no divider for the last element
 * Creates a divider [RecyclerView.ItemDecoration] that can be used with a
 * [LinearLayoutManager].
 *
 * @param context Current context, it will be used to access resources.
 * @param orientation Divider orientation. Should be [.HORIZONTAL] or [.VERTICAL].
 */
class AppJunkElementDivider constructor(
    context: Context,
    private val drawAfterLastItem: Boolean = false,
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
        val childCount = if (drawAfterLastItem) {
            parent.childCount
        } else {
            parent.childCount - 1
        }
        for (i in 0 until childCount) {
            val thisChild: View = parent.getChildAt(i)
            val nextChild: View? = parent.getChildAt(i + 1)

            val thisVH = parent.findContainingViewHolder(thisChild)
            val nextVH = nextChild?.let { parent.findContainingViewHolder(it) }

            when {
                thisVH is AppJunkElementHeaderVH -> continue
                thisVH is AppJunkElementFileCategoryVH -> continue
                thisVH is AppJunkElementFileVH && nextVH !is AppJunkElementFileVH -> continue
                else -> {} // NOOP
            }

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