package eu.darken.sdmse.main.ui.settings

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.uix.Fragment2
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SettingsFragmentBinding
import kotlinx.parcelize.Parcelize

@AndroidEntryPoint
class SettingsFragment : Fragment2(R.layout.settings_fragment),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private val vm: SettingsViewModel by viewModels()
    private val ui: SettingsFragmentBinding by viewBinding()

    val toolbar: Toolbar
        get() = ui.toolbar

    private val screens = ArrayList<Screen>()

    @Parcelize
    data class Screen(
        val fragmentClass: String,
        val screenTitle: String?
    ) : Parcelable

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        childFragmentManager.addOnBackStackChangedListener {
            val backStackCnt = childFragmentManager.backStackEntryCount
            val newScreenInfo = when {
                backStackCnt < screens.size -> {
                    // We popped the backstack, restore the underlying screen infos
                    // If there are none left, we are at the index again
                    screens.removeLastOrNull()
                    screens.lastOrNull() ?: Screen(
                        fragmentClass = SettingsIndexFragment::class.qualifiedName!!,
                        screenTitle = getString(eu.darken.sdmse.common.R.string.general_settings_title)
                    )
                }

                else -> {
                    // We added the current fragment to the stack, the new fragment's infos were already set, do nothing.
                    null
                }
            }

            newScreenInfo?.let { setCurrentScreenInfo(it) }
        }

        if (savedInstanceState == null) {
            val currentFragment = childFragmentManager.findFragmentById(R.id.content_frame)
            if (currentFragment == null) {
                childFragmentManager
                    .beginTransaction()
                    .add(R.id.content_frame, SettingsIndexFragment())
                    .commit()
            }
        } else {
            savedInstanceState.getParcelableArrayList<Screen>(BKEY_SCREEN_INFOS)?.let {
                screens.addAll(it)
            }
            screens.lastOrNull()?.let { setCurrentScreenInfo(it) }
        }

        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.contentFrame)
        }

        ui.toolbar.setNavigationOnClickListener { requireActivity().onBackPressed() }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(BKEY_SCREEN_INFOS, screens)
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val screenInfo = Screen(
            fragmentClass = pref.fragment!!,
            screenTitle = pref.title?.toString()
        )

        val args = Bundle().apply {
            putAll(pref.extras)
            putString(BKEY_SCREEN_TITLE, screenInfo.screenTitle)
        }

        val fragment = childFragmentManager.fragmentFactory
            .instantiate(this::class.java.classLoader!!, pref.fragment!!)
            .apply {
                arguments = args
                setTargetFragment(caller, 0)
            }

        setCurrentScreenInfo(screenInfo)
        screens.add(screenInfo)

        childFragmentManager.beginTransaction().apply {
            replace(R.id.content_frame, fragment)
            addToBackStack(null)
        }.commit()

        return true
    }


    private fun setCurrentScreenInfo(info: Screen) {
        ui.toolbar.apply {
            title = info.screenTitle
        }
    }

    companion object {
        private const val BKEY_SCREEN_TITLE = "preferenceScreenTitle"
        private const val BKEY_SCREEN_INFOS = "preferenceScreenInfos"
    }
}
