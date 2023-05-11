package eu.darken.sdmse.common.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ClipboardHelper
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.databinding.BrowsingbarBreadcrumbbarViewBinding
import java.io.File

class BreadCrumbBar<ItemT : APath> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = R.style.BreadCrumbBarStyle
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val ui = BrowsingbarBreadcrumbbarViewBinding.inflate(layoutInflator, this)

    private val crumbs = mutableListOf<ItemT>()
    var crumbListener: ((ItemT) -> Unit)? = null
    var crumbNamer: ((ItemT) -> String)? = null

    val currentCrumb: ItemT?
        get() = if (crumbs.isEmpty()) null else crumbs[crumbs.size - 1]

    override fun onFinishInflate() {
        if (isInEditMode) {
            setCrumbs(
                listOf(
                    *RawPath.build("/this/is/darkens/test").path.split(File.separator.toRegex())
                        .dropLastWhile { it.isEmpty() }.toTypedArray()
                ) as List<ItemT>
            )
        } else ui.pathContainerScrollview.isSmoothScrollingEnabled = true

        super.onFinishInflate()
    }

    fun setCrumbs(crumbs: List<ItemT>) {
        this.crumbs.clear()
        this.crumbs.addAll(crumbs)
        updateBar()
    }

    private fun updateBar() {
        ui.pathContainer.removeAllViews()
        for (crumb in crumbs) {
            val item = layoutInflator.inflate(R.layout.browsingbar_crumb_view, ui.pathContainer, false)

            val name = item.findViewById<TextView>(R.id.crumb_name)
            name.text = crumbNamer?.invoke(crumb) ?: crumb.toString()

            item.setOnLongClickListener {
                ClipboardHelper(context).copyToClipboard(crumb.toString())
                Toast.makeText(context, crumb.toString(), Toast.LENGTH_SHORT).show()
                true
            }
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )

            if (crumbs[crumbs.size - 1] == crumb) {
                name.setTextColor(context.getColorForAttr(com.google.android.material.R.attr.colorSecondary))
            }

            item.layoutParams = layoutParams
            item.setOnClickListener { crumbListener?.invoke(crumb) }
            ui.pathContainer.addView(item)
        }
        ui.pathContainerScrollview.requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        ui.pathContainerScrollview.fullScroll(View.FOCUS_RIGHT)
    }

    //    public static List<SDMFile> makeCrumbs(@NonNull SDMFile bread) {
    //        List<SDMFile> crumbs = new ArrayList<>();
    //        crumbs.add(bread);
    //        while (bread.getParentFile() != null) {
    //            crumbs.add(0, bread.getParentFile());
    //            bread = bread.getParentFile();
    //            Check.notNull(bread);
    //        }
    //        return crumbs;
    //    }

}
