package eu.darken.sdmse.main.ui.dashboard.items

import android.text.SpannableStringBuilder
import android.view.ViewGroup
import androidx.annotation.StringRes
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.toColored
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.databinding.DashboardTitleItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class TitleCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<TitleCardVH.Item, DashboardTitleItemBinding>(R.layout.dashboard_title_item, parent) {

    override val viewBinding = lazy { DashboardTitleItemBinding.bind(itemView) }

    private val slogan by lazy { getRngSlogan() }

    override val onBindData: DashboardTitleItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        if (item.upgradeInfo?.isPro == true) {
            val builder = SpannableStringBuilder(getString(R.string.app_name))

            val postFix = getString(R.string.app_name_upgrade_postfix).toColored(context, R.color.colorUpgraded)
            builder.append(" ").append(postFix)

            title.text = builder
        } else {
            title.text = getString(R.string.app_name)
        }

        subtitle.text = getString(slogan)
    }

    data class Item(
        val upgradeInfo: UpgradeRepo.Info?
    ) : DashboardAdapter.Item {

        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

    companion object {
        @StringRes
        fun getRngSlogan() = when ((0..2).random()) {
            0 -> R.string.slogan_message_0
            1 -> R.string.slogan_message_1
            2 -> R.string.slogan_message_2
            else -> throw IllegalArgumentException()
        }
    }
}