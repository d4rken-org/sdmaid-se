package eu.darken.sdmse.systemcleaner.ui.details

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.ui.details.AppCleanerDetailsFragmentVM
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SystemcleanerDetailsFragmentBinding

@AndroidEntryPoint
class SystemCleanerDetailsFragment : Fragment3(R.layout.systemcleaner_details_fragment) {

    override val vm: AppCleanerDetailsFragmentVM by viewModels()
    override val ui: SystemcleanerDetailsFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> super.onOptionsItemSelected(it)
                }
            }

        }


        super.onViewCreated(view, savedInstanceState)
    }
}
