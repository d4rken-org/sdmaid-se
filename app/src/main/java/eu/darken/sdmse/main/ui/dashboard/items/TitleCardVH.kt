package eu.darken.sdmse.main.ui.dashboard.items

import android.text.SpannableStringBuilder
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.toColored
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.databinding.DashboardTitleItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter


class TitleCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<TitleCardVH.Item, DashboardTitleItemBinding>(R.layout.dashboard_title_item, parent) {

    override val viewBinding = lazy { DashboardTitleItemBinding.bind(itemView) }

    private val slogan by lazy { getRngSlogan() }

    private val wiggleAnim = AnimationUtils.loadAnimation(context, R.anim.anim_wiggle)

    override val onBindData: DashboardTitleItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        mascotContainer.apply {
            var clickCount = 0
            setOnClickListener {
                clickCount++
                if (clickCount % 5 == 0) startAnimation(wiggleAnim)
            }
        }

        if (item.upgradeInfo?.isPro == true) {
            val builder = SpannableStringBuilder(getString(eu.darken.sdmse.common.R.string.app_name))

            val postFix = getString(R.string.app_name_upgrade_postfix).toColored(context, R.color.colorUpgraded)
            builder.append(" ").append(postFix)

            title.text = builder
        } else {
            title.text = getString(eu.darken.sdmse.common.R.string.app_name)
        }

        subtitle.text = getString(slogan)

        ribbon.apply {
            isVisible = BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE
            setOnClickListener { item.onRibbonClicked() }
        }
        ribbonPrimary.text = when (BuildConfigWrap.BUILD_TYPE) {
            BuildConfigWrap.BuildType.DEV -> "Dev"
            BuildConfigWrap.BuildType.BETA -> "Beta"
            BuildConfigWrap.BuildType.RELEASE -> ""
        }
        ribbonSecondary.text = BuildConfigWrap.VERSION_NAME
    }

    data class Item(
        val upgradeInfo: UpgradeRepo.Info?,
        val isWorking: Boolean,
        val onRibbonClicked: () -> Unit,
    ) : DashboardAdapter.Item {

        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

    companion object {
        @StringRes
        fun getRngSlogan() = when ((0..5).random()) {
            0 -> eu.darken.sdmse.common.R.string.slogan_message_0
            1 -> eu.darken.sdmse.common.R.string.slogan_message_1
            2 -> eu.darken.sdmse.common.R.string.slogan_message_2
            3 -> eu.darken.sdmse.common.R.string.slogan_message_3
            4 -> eu.darken.sdmse.common.R.string.slogan_message_4
            5 -> eu.darken.sdmse.common.R.string.slogan_message_5
            else -> throw IllegalArgumentException()
        }
    }
}