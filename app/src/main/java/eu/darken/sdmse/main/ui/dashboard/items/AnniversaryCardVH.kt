package eu.darken.sdmse.main.ui.dashboard.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DashboardAnniversaryItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class AnniversaryCardVH(
    parent: ViewGroup
) : DashboardAdapter.BaseVH<AnniversaryCardVH.Item, DashboardAnniversaryItemBinding>(
    R.layout.dashboard_anniversary_item,
    parent
) {

    override val viewBinding = lazy { DashboardAnniversaryItemBinding.bind(itemView) }

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.LONG)
        .withZone(ZoneId.systemDefault())

    override val onBindData: DashboardAnniversaryItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        installDate.text = getString(
            R.string.anniversary_card_install_date,
            dateFormatter.format(item.installDate)
        )

        body.text = resources.getQuantityString(
            R.plurals.anniversary_card_body,
            item.years,
            item.years, item.spaceFreed,
        )

        shareAction.setOnClickListener { item.onShare(item.years) }
        dismissAction.setOnClickListener { item.onDismiss() }
        root.setOnClickListener { shareAction.performClick() }
    }

    data class Item(
        val years: Int,
        val installDate: Instant,
        val spaceFreed: String,
        val onShare: (Int) -> Unit,
        val onDismiss: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }
}