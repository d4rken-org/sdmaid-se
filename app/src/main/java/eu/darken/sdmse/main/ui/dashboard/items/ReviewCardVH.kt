package eu.darken.sdmse.main.ui.dashboard.items

import android.app.Activity
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DashboardReviewItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class ReviewCardVH(
    private val activity: Activity,
    parent: ViewGroup
) : DashboardAdapter.BaseVH<ReviewCardVH.Item, DashboardReviewItemBinding>(
    R.layout.dashboard_review_item,
    parent
) {

    override val viewBinding = lazy { DashboardReviewItemBinding.bind(itemView) }

    override val onBindData: DashboardReviewItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        reviewAction.setOnClickListener { item.onReview(activity) }
        dismissAction.setOnClickListener { item.onDismiss() }
        root.setOnClickListener { reviewAction.performClick() }
    }

    data class Item(
        val onReview: (Activity) -> Unit,
        val onDismiss: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}