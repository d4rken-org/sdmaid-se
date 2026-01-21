package eu.darken.sdmse.deduplicator.ui.settings.arbiter

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DeduplicatorArbiterConfigRowBinding
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium


class ArbiterCriteriumRowVH(parent: ViewGroup) :
    ArbiterConfigAdapter.BaseVH<ArbiterConfigAdapter.CriteriumItem, DeduplicatorArbiterConfigRowBinding>(
        R.layout.deduplicator_arbiter_config_row,
        parent,
    ) {

    override val viewBinding = lazy { DeduplicatorArbiterConfigRowBinding.bind(itemView) }

    @SuppressLint("ClickableViewAccessibility")
    override val onBindData: DeduplicatorArbiterConfigRowBinding.(
        item: ArbiterConfigAdapter.CriteriumItem,
        payloads: List<Any>,
    ) -> Unit = binding { item ->
        val criterium = item.criterium

        icon.setImageResource(getIconForCriterium(criterium))
        title.text = getTitleForCriterium(criterium)
        description.text = getDescriptionForCriterium(criterium)

        mode.text = when (criterium) {
            is ArbiterCriterium.PreferredPath -> {
                if (criterium.keepPreferPaths.isEmpty()) {
                    context.getString(R.string.deduplicator_arbiter_configure_paths_action)
                } else {
                    criterium.keepPreferPaths.joinToString("\n") { it.userReadablePath.get(context) }
                }
            }

            else -> criterium.criteriumMode()?.labelRes?.let { context.getString(it) }
        }

        root.setOnClickListener { item.onModeClicked(item) }

        dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                (bindingAdapter as? ArbiterConfigAdapter)?.onStartDrag(this@ArbiterCriteriumRowVH)
            }
            false
        }
    }

    private fun getTitleForCriterium(criterium: ArbiterCriterium): String = context.getString(
        when (criterium) {
            is ArbiterCriterium.DuplicateType -> R.string.deduplicator_arbiter_criterium_duplicate_type_title
            is ArbiterCriterium.MediaProvider -> R.string.deduplicator_arbiter_criterium_media_provider_title
            is ArbiterCriterium.Location -> R.string.deduplicator_arbiter_criterium_location_title
            is ArbiterCriterium.Nesting -> R.string.deduplicator_arbiter_criterium_nesting_title
            is ArbiterCriterium.Modified -> R.string.deduplicator_arbiter_criterium_modified_title
            is ArbiterCriterium.Size -> R.string.deduplicator_arbiter_criterium_size_title
            is ArbiterCriterium.PreferredPath -> R.string.deduplicator_arbiter_criterium_preferred_path_title
        }
    )

    private fun getDescriptionForCriterium(criterium: ArbiterCriterium): String = context.getString(
        when (criterium) {
            is ArbiterCriterium.DuplicateType -> R.string.deduplicator_arbiter_criterium_duplicate_type_description
            is ArbiterCriterium.MediaProvider -> R.string.deduplicator_arbiter_criterium_media_provider_description
            is ArbiterCriterium.Location -> R.string.deduplicator_arbiter_criterium_location_description
            is ArbiterCriterium.Nesting -> R.string.deduplicator_arbiter_criterium_nesting_description
            is ArbiterCriterium.Modified -> R.string.deduplicator_arbiter_criterium_modified_description
            is ArbiterCriterium.Size -> R.string.deduplicator_arbiter_criterium_size_description
            is ArbiterCriterium.PreferredPath -> R.string.deduplicator_arbiter_criterium_preferred_path_description
        }
    )

    @DrawableRes
    private fun getIconForCriterium(criterium: ArbiterCriterium): Int = when (criterium) {
        is ArbiterCriterium.DuplicateType -> R.drawable.ic_code_equal_box_24
        is ArbiterCriterium.PreferredPath -> R.drawable.ic_folder_home_24
        is ArbiterCriterium.MediaProvider -> R.drawable.ic_multimedia_24
        is ArbiterCriterium.Location -> R.drawable.ic_sd_24
        is ArbiterCriterium.Nesting -> R.drawable.ic_contain_24
        is ArbiterCriterium.Modified -> R.drawable.ic_file_clock_outline_24
        is ArbiterCriterium.Size -> R.drawable.ic_weight_24
    }
}
