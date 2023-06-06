package eu.darken.sdmse.common.lists

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.darken.sdmse.common.DividerItemDecoration2
import me.zhanghai.android.fastscroll.FastScrollerBuilder

fun RecyclerView.setupDefaults(
    adapter: RecyclerView.Adapter<*>? = null,
    dividers: Boolean = true,
    fastscroll: Boolean = true,
) = apply {
    layoutManager = LinearLayoutManager(context)
    itemAnimator = DefaultItemAnimator()
    if (dividers) addItemDecoration(
        DividerItemDecoration2(
            context,
            DividerItemDecoration.VERTICAL,
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