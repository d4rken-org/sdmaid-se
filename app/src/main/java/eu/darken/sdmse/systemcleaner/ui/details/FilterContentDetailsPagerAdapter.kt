package eu.darken.sdmse.systemcleaner.ui.details

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import eu.darken.sdmse.common.uix.DetailsPagerAdapter3
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.FilterContentFragment
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.FilterContentFragmentArgs

class FilterContentDetailsPagerAdapter(
    private val activity: FragmentActivity,
    fm: FragmentManager,
) : DetailsPagerAdapter3<FilterContent>(activity, fm) {

    override fun onCreateFragment(item: FilterContent): Fragment = FilterContentFragment().apply {
        arguments = FilterContentFragmentArgs(item.identifier).toBundle()
    }

    override fun getPageTitle(position: Int): CharSequence = data[position].label.get(activity)

    override fun getItemPosition(obj: Any): Int = data
        .firstOrNull { set ->
            val fragment = obj as Fragment
            set.identifier == FilterContentFragmentArgs.fromBundle(fragment.requireArguments()).identifier
        }
        ?.let { data.indexOf(it) }
        ?: POSITION_NONE
}