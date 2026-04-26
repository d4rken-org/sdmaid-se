package eu.darken.sdmse.common.lists

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import eu.darken.sdmse.common.DividerItemDecoration2
import me.zhanghai.android.fastscroll.FastScrollerBuilder

fun RecyclerView.setupDefaults(
    adapter: RecyclerView.Adapter<*>? = null,
    verticalDividers: Boolean = true,
    horizontalDividers: Boolean = false,
    fastscroll: Boolean = true,
    layouter: LayoutManager = LinearLayoutManager(context)
) = apply {
    layoutManager = layouter
    itemAnimator = DefaultItemAnimator()

    if (verticalDividers) addItemDecoration(
        DividerItemDecoration2(
            context,
            DividerItemDecoration.VERTICAL,
            drawAfterLastItem = false
        )
    )

    if (horizontalDividers) addItemDecoration(
        DividerItemDecoration2(
            context,
            DividerItemDecoration.HORIZONTAL,
            drawAfterLastItem = false
        )
    )

    if (adapter != null) this.adapter = adapter

    if (fastscroll) {
        FastScrollerBuilder(this).apply {
            useMd2Style()
        }.build()
    }
}
