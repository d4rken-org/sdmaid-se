package eu.darken.sdmse.appcleaner.ui.details

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.ui.details.appjunk.AppJunkFragment
import eu.darken.sdmse.appcleaner.ui.details.appjunk.AppJunkViewModel
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.uix.DetailsPagerAdapter3

class AppJunkDetailsPagerAdapter(
    private val activity: FragmentActivity,
    fm: FragmentManager,
) : DetailsPagerAdapter3<AppJunk>(activity, fm) {

    override fun getPageWidth(position: Int): Float = 1f / context.getSpanCount()

    override fun onCreateFragment(item: AppJunk): Fragment = AppJunkFragment().apply {
        arguments = AppJunkViewModel.Args(identifier = item.identifier).toBundle()
    }

    override fun getPageTitle(position: Int): CharSequence = data[position].label.get(activity)

    override fun getItemPosition(obj: Any): Int = data
        .firstOrNull { set ->
            val fragment = obj as Fragment
            @Suppress("DEPRECATION")
            set.identifier == fragment.requireArguments().getParcelable<InstallId>("identifier")
        }
        ?.let { data.indexOf(it) }
        ?: POSITION_NONE
}