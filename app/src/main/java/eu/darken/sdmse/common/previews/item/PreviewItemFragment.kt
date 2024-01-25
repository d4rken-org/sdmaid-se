package eu.darken.sdmse.common.previews.item

import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import coil.load
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.DialogFragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.PreviewItemFragmentBinding

@AndroidEntryPoint
class PreviewItemFragment : DialogFragment3(R.layout.preview_item_fragment) {

    override val vm: PreviewItemViewModel by viewModels()
    override val ui: PreviewItemFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(ui) { state ->
            previewImage.apply {
                isGone = state.lookup == null
                state.lookup?.let { load(it) }
            }
            progress.isGone = state.progress == null

            previewTitle.text = state.lookup?.path
            previewSubtitle.text = state.lookup?.let { Formatter.formatFileSize(requireContext(), it.size) }

            previewFooterContainer.isGone = state.lookup == null
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
