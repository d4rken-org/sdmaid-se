package eu.darken.sdmse.stats.ui.spacehistory

import android.os.Bundle
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.StatsSpaceHistoryFragmentBinding
import eu.darken.sdmse.main.core.iconRes
import eu.darken.sdmse.main.core.labelRes
import eu.darken.sdmse.stats.core.db.ReportEntity
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

@AndroidEntryPoint
class SpaceHistoryFragment : Fragment3(R.layout.stats_space_history_fragment) {

    override val vm: SpaceHistoryViewModel by viewModels()
    override val ui: StatsSpaceHistoryFragmentBinding by viewBinding()

    private var activePopup: PopupWindow? = null
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbarlayout, top = true)
            insetsPadding(ui.scrollView, bottom = true)
            insetsPadding(ui.loadingOverlay, bottom = true)
        }

        ui.toolbar.setupWithNavController(findNavController())

        ui.chart.setOnMarkerTapListener { report, screenX, screenY ->
            showMarkerTooltip(report, screenX, screenY)
        }

        ui.rangeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            when (checkedId) {
                R.id.range_7d -> vm.selectRange(SpaceHistoryViewModel.Range.DAYS_7)
                R.id.range_30d -> vm.selectRange(SpaceHistoryViewModel.Range.DAYS_30)
                R.id.range_90d -> vm.selectRange(SpaceHistoryViewModel.Range.DAYS_90)
            }
        }

        ui.upgradeAction.setOnClickListener { vm.openUpgrade() }

        vm.state.observe2(ui) { state ->
            loadingOverlay.isGone = true
            contentContainer.isVisible = true

            when (state.selectedRange) {
                SpaceHistoryViewModel.Range.DAYS_7 -> range7d.isChecked = true
                SpaceHistoryViewModel.Range.DAYS_30 -> range30d.isChecked = true
                SpaceHistoryViewModel.Range.DAYS_90 -> range90d.isChecked = true
            }
            range30d.isEnabled = state.isPro
            range90d.isEnabled = state.isPro

            chart.setData(state.snapshots)
            chart.setReports(state.reportMarkers)

            currentValue.text = state.currentUsed?.let { Formatter.formatShortFileSize(requireContext(), it) } ?: "-"
            minValue.text = state.minUsed?.let { Formatter.formatShortFileSize(requireContext(), it) } ?: "-"
            maxValue.text = state.maxUsed?.let { Formatter.formatShortFileSize(requireContext(), it) } ?: "-"
            deltaValue.text = state.deltaUsed?.let { delta ->
                val absDelta = Formatter.formatShortFileSize(requireContext(), delta.absoluteValue)
                val signed = when {
                    delta > 0 -> "+$absDelta"
                    delta < 0 -> "-$absDelta"
                    else -> absDelta
                }
                getString(R.string.stats_space_history_delta_in_x, signed, rangeToLabel(state.selectedRange))
            } ?: "-"

            deltaValue.setTextColor(
                when {
                    state.deltaUsed == null -> deltaValue.currentTextColor
                    state.deltaUsed > 0 -> deltaValue.context.getColorForAttr(android.R.attr.colorError)
                    state.deltaUsed < 0 -> deltaValue.context.getColorForAttr(androidx.appcompat.R.attr.colorPrimary)
                    else -> deltaValue.context.getColorForAttr(android.R.attr.textColorSecondary)
                }
            )

            storageTitle.isVisible = state.storages.size > 1
            storageChipGroup.isVisible = state.storages.isNotEmpty()
            storageChipGroup.removeAllViews()
            state.storages.forEach { storage ->
                val chip = (layoutInflater.inflate(
                    R.layout.stats_space_history_storage_chip, storageChipGroup, false
                ) as Chip).apply {
                    text = storage.label.get(context)
                    isCheckable = true
                    isChecked = storage.id == state.selectedStorageId
                    setOnClickListener { vm.selectStorage(storage.id) }
                }
                storageChipGroup.addView(chip)
            }

            upgradeCard.isVisible = state.showUpgradePrompt
        }

        super.onViewCreated(view, savedInstanceState)
    }

    private fun showMarkerTooltip(report: ReportEntity, screenX: Int, screenY: Int) {
        dismissTooltip()

        val tooltipView = layoutInflater.inflate(R.layout.stats_space_history_marker_tooltip, null)
        tooltipView.findViewById<ImageView>(R.id.tooltip_icon).setImageResource(report.tool.iconRes)
        tooltipView.findViewById<TextView>(R.id.tooltip_tool_name).setText(report.tool.labelRes)

        val detail = tooltipView.findViewById<TextView>(R.id.tooltip_detail)
        val time = report.endAt.atZone(ZoneId.systemDefault()).format(timeFormatter)
        detail.text = if (report.affectedSpace != null && report.affectedSpace > 0) {
            val freed = ByteFormatter.formatSize(requireContext(), report.affectedSpace).first
            "${getString(R.string.stats_space_history_marker_freed, freed)} · $time"
        } else {
            time
        }

        val popup = PopupWindow(
            tooltipView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            elevation = 8f
            setOnDismissListener { ui.chart.clearSelection() }
        }

        tooltipView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        val margin = (12 * resources.displayMetrics.density).toInt()
        val popupX = screenX - tooltipView.measuredWidth / 2
        val popupY = screenY - tooltipView.measuredHeight - margin

        popup.showAtLocation(ui.chart, Gravity.NO_GRAVITY, popupX, popupY)
        activePopup = popup
    }

    private fun dismissTooltip() {
        activePopup?.dismiss()
        activePopup = null
    }

    override fun onDestroyView() {
        dismissTooltip()
        super.onDestroyView()
    }

    private fun rangeToLabel(range: SpaceHistoryViewModel.Range): String = when (range) {
        SpaceHistoryViewModel.Range.DAYS_7 -> getString(R.string.stats_space_history_range_7d)
        SpaceHistoryViewModel.Range.DAYS_30 -> getString(R.string.stats_space_history_range_30d)
        SpaceHistoryViewModel.Range.DAYS_90 -> getString(R.string.stats_space_history_range_90d)
    }
}
