package eu.darken.sdmse.swiper.ui.sessions.items

import android.text.TextUtils
import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.textview.MaterialTextView
import eu.darken.sdmse.swiper.R
import androidx.core.view.isGone
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.swiper.databinding.SwiperSessionsSessionItemBinding
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.swiper.core.FileTypeCategory
import eu.darken.sdmse.swiper.core.SessionState
import eu.darken.sdmse.swiper.core.SortOrder
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.swiper.ui.sessions.SwiperSessionsAdapter
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.common.R as CommonR

class SwiperSessionsSessionVH(parent: ViewGroup) :
    SwiperSessionsAdapter.BaseVH<SwiperSessionsSessionVH.Item, SwiperSessionsSessionItemBinding>(
        R.layout.swiper_sessions_session_item,
        parent,
    ) {

    override val viewBinding = lazy { SwiperSessionsSessionItemBinding.bind(itemView) }

    override val onBindData: SwiperSessionsSessionItemBinding.(
        item: Item,
        payloads: List<Any>,
    ) -> Unit = binding { item ->
        val sessionWithStats = item.sessionWithStats
        val session = sessionWithStats.session

        // Session title - custom label or "Session #N"
        sessionTitle.text = session.label
            ?: getString(eu.darken.sdmse.swiper.R.string.swiper_session_default_label, item.position)

        // Title container for renaming (tap anywhere on title + icon)
        titleContainer.setOnClickListener { item.onRename() }

        // Timestamps - combined on single line
        val createdRelative = DateUtils.getRelativeTimeSpanString(
            session.createdAt.toEpochMilli(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
        val lastActivityRelative = DateUtils.getRelativeTimeSpanString(
            session.lastModifiedAt.toEpochMilli(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
        timestamps.text = getString(eu.darken.sdmse.swiper.R.string.swiper_session_timestamps, createdRelative, lastActivityRelative)

        // Paths section - one path per line with middle ellipsis
        pathsContainer.removeAllViews()
        session.sourcePaths.forEach { path ->
            val pathView = MaterialTextView(context).apply {
                text = path.userReadablePath.get(context)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            }
            pathsContainer.addView(pathView)
        }

        // Filter summary
        val filter = session.fileTypeFilter
        if (!filter.isEmpty) {
            val parts = mutableListOf<String>()
            filter.categories.sortedBy { it.ordinal }.forEach { category ->
                val name = when (category) {
                    FileTypeCategory.IMAGES -> getString(R.string.swiper_file_type_category_images)
                    FileTypeCategory.VIDEOS -> getString(R.string.swiper_file_type_category_videos)
                    FileTypeCategory.AUDIO -> getString(R.string.swiper_file_type_category_audio)
                    FileTypeCategory.DOCUMENTS -> getString(R.string.swiper_file_type_category_documents)
                    FileTypeCategory.ARCHIVES -> getString(R.string.swiper_file_type_category_archives)
                }
                parts.add(name)
            }
            filter.customExtensions.sorted().forEach { ext -> parts.add(".$ext") }
            filterSummary.text = getString(R.string.swiper_file_type_filter_summary, parts.joinToString(", "))
            filterSummary.isVisible = true
        } else {
            filterSummary.isVisible = false
        }

        // Stats - only show after scan
        val isScanned = sessionWithStats.isScanned
        val noMatchingFiles = isScanned && session.totalItems == 0
        noMatchingFiles.let { noMatch ->
            this.noMatchingFiles.isVisible = noMatch
        }
        statusLabel.isVisible = isScanned && !noMatchingFiles
        statsContainer.isVisible = isScanned && !noMatchingFiles
        progressContainer.isVisible = isScanned && !noMatchingFiles

        if (isScanned) {
            // Progress bar
            val totalItems = session.totalItems
            val decidedItems = sessionWithStats.keepCount + sessionWithStats.deleteCount
            val progressPercent = if (totalItems > 0) (decidedItems * 100 / totalItems) else 100
            sessionProgressBar.progress = progressPercent
            sessionProgressText.text = "$progressPercent%"

            keepCount.text = context.resources.getQuantityString(
                eu.darken.sdmse.swiper.R.plurals.swiper_session_status_to_keep,
                sessionWithStats.keepCount,
                sessionWithStats.keepCount,
            )
            deleteCount.text = context.resources.getQuantityString(
                eu.darken.sdmse.swiper.R.plurals.swiper_session_status_to_delete,
                sessionWithStats.deleteCount,
                sessionWithStats.deleteCount,
            )
            openCount.text = context.resources.getQuantityString(
                eu.darken.sdmse.swiper.R.plurals.swiper_session_status_undecided,
                sessionWithStats.undecidedCount,
                sessionWithStats.undecidedCount,
            )

            // Sizes are not available until session is opened (fresh lookups)
            keepSize.isGone = true
            deleteSize.isGone = true
            undecidedSize.isGone = true
        }

        // Remove button - always visible
        removeAction.isVisible = true
        removeAction.setOnClickListener { item.onRemove() }

        // Filter + sort icons - visible before scan or when scan found no matches
        val showFilter = !item.isScanning && !item.isRefreshing &&
            (session.state == SessionState.CREATED || noMatchingFiles)
        filterAction.isVisible = showFilter
        sortAction.isVisible = showFilter
        if (showFilter) {
            filterAction.setOnClickListener { item.onFilter() }
            val hasActiveFilter = !filter.isEmpty
            val tintAttr = if (hasActiveFilter) {
                androidx.appcompat.R.attr.colorPrimary
            } else {
                com.google.android.material.R.attr.colorOnSurfaceVariant
            }
            val tint = android.content.res.ColorStateList.valueOf(context.getColorForAttr(tintAttr))
            filterAction.iconTint = tint
            filterAction.setTextColor(tint)

            sortAction.setOnClickListener { item.onSortOrder() }
            val isCustomSort = session.sortOrder != SortOrder.DEFAULT
            val sortTintAttr = if (isCustomSort) {
                androidx.appcompat.R.attr.colorPrimary
            } else {
                com.google.android.material.R.attr.colorOnSurfaceVariant
            }
            val sortTint = android.content.res.ColorStateList.valueOf(context.getColorForAttr(sortTintAttr))
            sortAction.iconTint = sortTint
            sortAction.setTextColor(sortTint)
        }

        // Action button - four states: not scanned, scanning, refreshing, scanned
        scanProgress.isVisible = item.isScanning || item.isRefreshing
        when {
            item.isScanning -> {
                // Scanning in progress - show cancel button (disabled if already cancelling)
                actionButton.text = getString(eu.darken.sdmse.common.R.string.general_cancel_action)
                actionButton.setIconResource(UiR.drawable.ic_cancel)
                actionButton.isEnabled = !item.isCancelling
                actionButton.setOnClickListener { item.onCancel() }
            }
            item.isRefreshing -> {
                // Refreshing lookups in progress
                actionButton.text = getString(eu.darken.sdmse.common.R.string.general_progress_loading)
                actionButton.icon = null
                actionButton.isEnabled = false
            }
            isScanned && session.totalItems > 0 -> {
                // Scan complete with items found
                val hasStarted = sessionWithStats.keepCount + sessionWithStats.deleteCount > 0
                actionButton.text = if (hasStarted) {
                    getString(eu.darken.sdmse.swiper.R.string.swiper_continue_action)
                } else {
                    getString(eu.darken.sdmse.swiper.R.string.swiper_start_action)
                }
                actionButton.setIconResource(CommonR.drawable.ic_baseline_swipe_24)
                actionButton.isEnabled = true
                actionButton.setOnClickListener { item.onContinue() }
            }
            isScanned && session.totalItems == 0 -> {
                // Scan complete but no matching files
                actionButton.text = getString(eu.darken.sdmse.common.R.string.general_scan_action)
                actionButton.setIconResource(UiR.drawable.ic_baseline_search_24)
                actionButton.isEnabled = true
                actionButton.setOnClickListener { item.onScan() }
            }
            else -> {
                // Not yet scanned
                actionButton.text = getString(eu.darken.sdmse.common.R.string.general_scan_action)
                actionButton.setIconResource(UiR.drawable.ic_baseline_search_24)
                actionButton.isEnabled = true
                actionButton.setOnClickListener { item.onScan() }
            }
        }

        // Card click - opens session when scanned and has items
        val canContinue = isScanned && session.totalItems > 0
        root.setOnClickListener { if (canContinue) item.onContinue() }
        root.isClickable = canContinue
    }

    data class Item(
        val sessionWithStats: Swiper.SessionWithStats,
        val position: Int,
        val isScanning: Boolean,
        val isCancelling: Boolean,
        val isRefreshing: Boolean,
        val onScan: () -> Unit,
        val onContinue: () -> Unit,
        val onRemove: () -> Unit,
        val onRename: () -> Unit,
        val onCancel: () -> Unit,
        val onFilter: () -> Unit,
        val onSortOrder: () -> Unit,
    ) : SwiperSessionsAdapter.Item {
        override val stableId: Long = sessionWithStats.session.sessionId.hashCode().toLong()
    }
}
