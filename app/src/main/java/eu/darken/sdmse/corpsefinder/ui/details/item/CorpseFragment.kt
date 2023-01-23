package eu.darken.sdmse.corpsefinder.ui.details.item

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.CorpsefinderCorpseFragmentBinding

@AndroidEntryPoint
class CorpseFragment : Fragment3(R.layout.corpsefinder_corpse_fragment) {

    override val vm: CorpseFragmentVM by viewModels()
    override val ui: CorpsefinderCorpseFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        vm.info.observe2 {

        }

        super.onViewCreated(view, savedInstanceState)
    }
}
