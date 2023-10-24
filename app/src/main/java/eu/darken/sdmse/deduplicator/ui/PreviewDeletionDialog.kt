package eu.darken.sdmse.deduplicator.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.SimpleVHCreatorMod
import eu.darken.sdmse.databinding.ViewDeleteConfirmationPreviewGridBinding
import eu.darken.sdmse.databinding.ViewDeleteConfirmationPreviewGridItemBinding
import eu.darken.sdmse.databinding.ViewDeleteConfirmationPreviewSingleBinding

class PreviewDeletionDialog(private val context: Context) {

    fun show(
        previews: List<Item>,
        onPositive: () -> Unit,
        onNegative: () -> Unit,
        onNeutral: () -> Unit,
    ): AlertDialog {
        val preview = if (previews.size == 1) {
            val binding = ViewDeleteConfirmationPreviewSingleBinding.inflate(
                LayoutInflater.from(context),
                null,
                false
            )

            binding.previewImage.loadFilePreview(previews.single().lookup)

            binding.root
        } else {
            val binding = ViewDeleteConfirmationPreviewGridBinding.inflate(
                LayoutInflater.from(context),
                null,
                false
            )

            val adapter = PreviewAdapter().apply {
                update(previews)
            }

            binding.apply {
                list.layoutManager = GridLayoutManager(
                    context,
                    5,
                    GridLayoutManager.VERTICAL,
                    false
                )
                list.adapter = adapter
                val maxHeight = context.dpToPx(192f)
                list.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        list.viewTreeObserver.removeOnPreDrawListener(this)
                        if (list.height > maxHeight) {
                            val layoutParams = list.layoutParams
                            layoutParams.height = maxHeight
                            list.layoutParams = layoutParams
                        }
                        return true
                    }
                })
            }

            binding.root
        }


        val dialog = MaterialAlertDialogBuilder(context).apply {
            setView(preview)
            setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
            setMessage(R.string.deduplicator_delete_all_confirmation_message)
            setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ -> onPositive() }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> onNegative() }
            setNeutralButton(eu.darken.sdmse.common.R.string.general_show_details_action) { _, _ -> onNeutral() }
        }.show()

        return dialog
    }

    class PreviewAdapter :
        ModularAdapter<PreviewAdapter.VH>(),
        HasAsyncDiffer<Item> {

        override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

        override fun getItemCount(): Int = data.size

        init {
            addMod(DataBinderMod(data))
            addMod(SimpleVHCreatorMod { VH(it) })
        }

        class VH(parent: ViewGroup) :
            ModularAdapter.VH(R.layout.view_delete_confirmation_preview_grid_item, parent),
            BindableVH<Item, ViewDeleteConfirmationPreviewGridItemBinding> {

            override val viewBinding = lazy { ViewDeleteConfirmationPreviewGridItemBinding.bind(itemView) }

            override val onBindData: ViewDeleteConfirmationPreviewGridItemBinding.(
                item: Item,
                payloads: List<Any>
            ) -> Unit = { item, _ ->
                previewImage.loadFilePreview(item.lookup)
            }

        }
    }

    data class Item(
        val lookup: APathLookup<*>,
    ) : DifferItem {
        override val stableId: Long = lookup.path.hashCode().toLong()
    }
}