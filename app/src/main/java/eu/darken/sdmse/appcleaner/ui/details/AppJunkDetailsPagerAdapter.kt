package eu.darken.sdmse.appcleaner.ui.details

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.ui.details.appjunk.AppJunkFragment
import eu.darken.sdmse.appcleaner.ui.details.appjunk.AppJunkFragmentArgs
import eu.darken.sdmse.common.uix.DetailsPagerAdapter3

class AppJunkDetailsPagerAdapter(
    private val activity: FragmentActivity,
    fm: FragmentManager,
) : DetailsPagerAdapter3<AppJunk>(activity, fm) {

    override fun onCreateFragment(item: AppJunk): Fragment = AppJunkFragment().apply {
        arguments = AppJunkFragmentArgs(item.identifier).toBundle()
    }

    override fun getPageTitle(position: Int): CharSequence = data[position].label.get(activity)

    override fun getItemPosition(obj: Any): Int = data
        .firstOrNull { set ->
            val fragment = obj as Fragment
            set.identifier == AppJunkFragmentArgs.fromBundle(fragment.requireArguments()).identifier
        }
        ?.let { data.indexOf(it) }
        ?: POSITION_NONE
}