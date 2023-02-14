package eu.darken.sdmse.systemcleaner.ui.details

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import eu.darken.sdmse.common.uix.DetailsPagerAdapter3
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.filter.getLabel
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.FilterContentFragment
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.FilterContentFragmentArgs

class FilterContentDetailsPagerAdapter(
    private val activity: FragmentActivity,
    fm: FragmentManager,
) : DetailsPagerAdapter3<FilterContent>(activity, fm) {

    override fun onCreateFragment(item: FilterContent): Fragment = FilterContentFragment().apply {
        arguments = FilterContentFragmentArgs(item.filterIdentifier).toBundle()
    }

    override fun getPageTitle(position: Int): CharSequence = data[position].filterIdentifier.getLabel(activity)

    override fun getItemPosition(obj: Any): Int = data
        .firstOrNull { set ->
            val fragment = obj as Fragment
            set.filterIdentifier == fragment.requireArguments().getString(PAGE_IDENTIFIER)
        }
        ?.let { data.indexOf(it) }
        ?: POSITION_NONE
}