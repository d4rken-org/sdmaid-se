package eu.darken.sdmse.common.views

import android.content.Context
import android.util.AttributeSet
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import eu.darken.sdmse.common.getColorForAttr

class ThemedSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwipeRefreshLayout(context, attrs) {

    override fun onFinishInflate() {
        setProgressBackgroundColorSchemeColor(context.getColorForAttr(com.google.android.material.R.attr.colorSurface))
        setColorSchemeColors(
            context.getColorForAttr(androidx.appcompat.R.attr.colorPrimary),
            context.getColorForAttr(androidx.appcompat.R.attr.colorPrimaryDark)
        )
        super.onFinishInflate()
    }

}