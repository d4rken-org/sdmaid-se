package eu.darken.sdmse.swiper.ui

import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.databinding.SwiperDashboardItemBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter
import eu.darken.sdmse.swiper.core.Swiper
import java.time.Duration
import java.time.Instant

class SwiperDashCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<SwiperDashCardVH.Item, SwiperDashboardItemBinding>(
        R.layout.swiper_dashboard_item,
        parent
    ) {

    override val viewBinding = lazy { SwiperDashboardItemBinding.bind(itemView) }

    override val onBindData: SwiperDashboardItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        progressBar.isVisible = item.progress != null

        val sessionsWithStats = item.sessionsWithStats
        when {
            sessionsWithStats.isEmpty() -> {
                // No sessions: show tool description
                subtitle.text = getString(R.string.swiper_dashcard_description)
                viewAction.text = getString(R.string.swiper_start_action)
            }
            sessionsWithStats.size == 1 -> {
                // Single session: show context "Continue session from X days ago (Y% finished)"
                val sessionWithStats = sessionsWithStats.first()
                val session = sessionWithStats.session
                val daysAgo = Duration.between(session.lastModifiedAt, Instant.now()).toDays().toInt()
                val finished = sessionWithStats.keepCount + sessionWithStats.deleteCount
                val percent = if (session.totalItems > 0) (finished * 100 / session.totalItems) else 0
                subtitle.text = getString(
                    R.string.swiper_dashcard_session_context,
                    daysAgo,
                    percent,
                )
                viewAction.text = getString(eu.darken.sdmse.common.R.string.general_view_action)
            }
            else -> {
                // Multiple sessions: show "X sessions, Y undecided items"
                val totalUndecided = sessionsWithStats.sumOf { it.undecidedCount }
                val sessionsText = getQuantityString(
                    R.plurals.swiper_dashcard_x_sessions,
                    sessionsWithStats.size,
                )
                val undecidedText = getQuantityString(
                    R.plurals.swiper_dashcard_x_undecided_items,
                    totalUndecided,
                )
                subtitle.text = "$sessionsText, $undecidedText"
                viewAction.text = getString(eu.darken.sdmse.common.R.string.general_view_action)
            }
        }

        root.setOnClickListener { item.onViewDetails() }
    }

    data class Item(
        val sessionsWithStats: List<Swiper.SessionWithStats>,
        val progress: Progress.Data?,
        val showProRequirement: Boolean,
        val onViewDetails: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }
}
