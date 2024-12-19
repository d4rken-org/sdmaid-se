package eu.darken.sdmse.main.ui.dashboard.items

import android.text.SpannableStringBuilder
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.WebpageTool
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
            val builder = SpannableStringBuilder(getString(eu.darken.sdmse.common.R.string.app_name))

            val postFix = getString(R.string.app_name_upgrade_postfix).toColored(context, R.color.colorUpgraded)
            builder.append(" ").append(postFix)

            title.text = builder
        } else {
            title.text = getString(eu.darken.sdmse.common.R.string.app_name)
        }

        subtitle.apply {
            text = getString(slogan)
            if (slogan == eu.darken.sdmse.common.R.string.slogan_message_8) {
                setOnLongClickListener {
                    item.webpageTool.open("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                    true
                }
            } else {
                setOnLongClickListener(null)
            }
        }

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

        mascotContainer.apply {
            val touchListener = View.OnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    item.onMascotTriggered(false)
                    setOnTouchListener(null)
                    performClick()
                }
                false
            }
            setOnLongClickListener {
                item.onMascotTriggered(true)
                setOnTouchListener(touchListener)
                true
            }
        }
    }

    data class Item(
        val upgradeInfo: UpgradeRepo.Info?,
        val isWorking: Boolean,
        val onRibbonClicked: () -> Unit,
        val webpageTool: WebpageTool,
        val onMascotTriggered: (Boolean) -> Unit,
    ) : DashboardAdapter.Item {

        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

    companion object {
        @StringRes
        fun getRngSlogan() = when ((0..8).random()) {
            0 -> eu.darken.sdmse.common.R.string.slogan_message_0
            1 -> eu.darken.sdmse.common.R.string.slogan_message_1
            2 -> eu.darken.sdmse.common.R.string.slogan_message_2
            3 -> eu.darken.sdmse.common.R.string.slogan_message_3
            4 -> eu.darken.sdmse.common.R.string.slogan_message_4
            5 -> eu.darken.sdmse.common.R.string.slogan_message_5
            6 -> eu.darken.sdmse.common.R.string.slogan_message_6
            7 -> eu.darken.sdmse.common.R.string.slogan_message_7
            8 -> eu.darken.sdmse.common.R.string.slogan_message_8
            else -> throw IllegalArgumentException()
        }
    }
}