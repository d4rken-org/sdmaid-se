package eu.darken.sdmse.deduplicator.ui

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import coil.transform.RoundedCornersTransformation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import dagger.hilt.android.qualifiers.ActivityContext
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
import eu.darken.sdmse.common.previews.PreviewFragmentArgs
import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.databinding.ViewDeleteConfirmationPreviewGridBinding
import eu.darken.sdmse.databinding.ViewDeleteConfirmationPreviewGridItemBinding
import eu.darken.sdmse.databinding.ViewDeleteConfirmationPreviewSingleBinding
import eu.darken.sdmse.deduplicator.core.Duplicate
import javax.inject.Inject

class PreviewDeletionDialog @Inject constructor(
    @ActivityContext private val context: Context,
) {

    private fun openPreview(options: PreviewOptions) {
        (context as Activity).findNavController(R.id.nav_host).navigate(
            resId = R.id.goToPreview,
            args = PreviewFragmentArgs(options = options).toBundle()
        )
    }

    sealed interface Mode {
        val allowDeleteAll: Boolean
        val count: Int
        val previews: List<APathLookup<*>>

        data class All(
            val clusters: Collection<Duplicate.Cluster>,
        ) : Mode {
            override val allowDeleteAll: Boolean = false
            override val count: Int
                get() = clusters.size
            override val previews: List<APathLookup<*>>
                get() = clusters.map { it.previewFile }
        }

        data class Clusters(
            val clusters: Collection<Duplicate.Cluster>,
            override val allowDeleteAll: Boolean
        ) : Mode {
            override val count: Int
                get() = clusters.size
            override val previews: List<APathLookup<*>>
                get() = clusters.map { it.previewFile }
        }

        data class Groups(
            val groups: Collection<Duplicate.Group>,
            override val allowDeleteAll: Boolean
        ) : Mode {
            override val count: Int
                get() = groups.size
            override val previews: List<APathLookup<*>>
                get() = groups.map { it.previewFile }
        }

        data class Duplicates(
            val duplicates: Collection<Duplicate>,
        ) : Mode {
            override val allowDeleteAll: Boolean = false
            override val count: Int
                get() = duplicates.size
            override val previews: List<APathLookup<*>>
                get() = duplicates.map { it.lookup }
        }
    }

    fun show(
        mode: Mode,
        onPositive: (Boolean) -> Unit,
        onNegative: () -> Unit = {},
        onNeutral: (() -> Unit)?,
    ): AlertDialog {
        var deleteAllToggle: MaterialSwitch? = null
        val preview = if (mode.count == 1) {
            val binding = ViewDeleteConfirmationPreviewSingleBinding.inflate(
                LayoutInflater.from(context),
                null,
                false
            )

            binding.deleteAllToggle.apply {
                isVisible = mode.allowDeleteAll
                if (mode.allowDeleteAll) deleteAllToggle = this
            }
            binding.deleteAllIcon.isVisible = binding.deleteAllToggle.isVisible

            binding.previewImage.loadFilePreview(mode.previews.single()) {
                transformations(RoundedCornersTransformation(36F))
            }

            binding.root.setOnClickListener { openPreview(PreviewOptions(mode.previews.single().lookedUp)) }

            binding.root
        } else {
            val binding = ViewDeleteConfirmationPreviewGridBinding.inflate(
                LayoutInflater.from(context),
                null,
                false
            )

            binding.deleteAllToggle.apply {
                isVisible = mode.allowDeleteAll
                if (mode.allowDeleteAll) deleteAllToggle = this
            }
            binding.deleteAllIcon.isVisible = binding.deleteAllToggle.isVisible

            val adapter = PreviewAdapter().apply {
                update(mode.previews.map { lookup ->
                    Item(
                        lookup = lookup,
                        onPreview = {
                            val options = PreviewOptions(
                                paths = mode.previews.map { it.lookedUp },
                                position = mode.previews.indexOf(lookup)
                            )
                            openPreview(options)
                        }
                    )
                })
            }

            binding.apply {
                val spanCount = 5
                list.layoutManager = GridLayoutManager(
                    context,
                    spanCount,
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
            setMessage(
                when (mode) {
                    is Mode.All -> context.getString(R.string.deduplicator_delete_confirmation_message)
                    is Mode.Clusters -> when (mode.count) {
                        1 -> context.getString(R.string.deduplicator_delete_single_set_confirmation_message)
                        else -> context.getString(R.string.deduplicator_delete_multiple_sets_confirmation_message)
                    }

                    is Mode.Groups -> when (mode.count) {
                        1 -> context.getString(R.string.deduplicator_delete_single_set_confirmation_message)
                        else -> context.getString(R.string.deduplicator_delete_multiple_sets_confirmation_message)
                    }

                    is Mode.Duplicates -> when (mode.count) {
                        1 -> context.getString(
                            eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                            mode.duplicates.single().lookup.userReadablePath.get(context),
                        )

                        else -> context.resources.getQuantityString(
                            eu.darken.sdmse.common.R.plurals.general_delete_confirmation_message_selected_x_items,
                            mode.count,
                            mode.count
                        )
                    }
                }
            )
            setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                onPositive(deleteAllToggle?.isChecked ?: false)
            }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> onNegative() }
            if (onNeutral != null) {
                setNeutralButton(eu.darken.sdmse.common.R.string.general_show_details_action) { _, _ -> onNeutral() }
            }
        }.show()


        deleteAllToggle?.setOnCheckedChangeListener { _, isChecked ->
            val newMsg = when (mode) {
                is Mode.All -> throw IllegalStateException("deleteAllToggle is unsupported in Mode.All")
                is Mode.Duplicates -> throw IllegalStateException("deleteAllToggle is unsupported in Mode.Duplicate")

                is Mode.Clusters, is Mode.Groups -> when (mode.count) {
                    1 -> when (isChecked) {
                        true -> R.string.deduplicator_delete_single_set_keep_none_confirmation_message
                        false -> R.string.deduplicator_delete_single_set_confirmation_message
                    }

                    else -> when (isChecked) {
                        true -> R.string.deduplicator_delete_multiple_sets_keep_none_confirmation_message
                        false -> R.string.deduplicator_delete_multiple_sets_confirmation_message
                    }
                }
            }
            dialog.findViewById<TextView>(android.R.id.message)!!.text = context.getString(newMsg)
        }

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
                previewImage.loadFilePreview(item.lookup) {
                    transformations(RoundedCornersTransformation(36F))
                }
                previewImage.setOnClickListener { item.onPreview() }
            }

        }
    }

    data class Item(
        val lookup: APathLookup<*>,
        val onPreview: () -> Unit,
    ) : DifferItem {
        override val stableId: Long = lookup.path.hashCode().toLong()
    }
}