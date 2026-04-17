package eu.darken.sdmse.main.ui.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.uix.Fragment2
import eu.darken.sdmse.common.uix.ToolbarHost
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SettingsFragmentBinding

@AndroidEntryPoint
class SettingsFragment : Fragment2(R.layout.settings_fragment),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    ToolbarHost {

    private val vm: SettingsViewModel by viewModels()
    private val ui: SettingsFragmentBinding by viewBinding()

    override val toolbar: Toolbar
        get() = ui.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Drive the toolbar from fragment lifecycle + fragment arguments.
        // We can't trust childFragmentManager.backStackEntryCount inside the resumed
        // callback: on Android 16's predictive-back path, the new fragment becomes
        // RESUMED before the back stack has actually been decremented, so reading
        // the count here would give a stale value.
        childFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    if (f.id == R.id.content_frame && view != null) {
                        syncToolbarForFragment(f)
                    }
                }
            },
            false,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbarlayout, top = true)
        }

        if (savedInstanceState == null
            && childFragmentManager.findFragmentById(R.id.content_frame) == null
        ) {
            childFragmentManager
                .beginTransaction()
                .add(R.id.content_frame, SettingsIndexFragment())
                .commit()
        }

        // Sync on first view creation AND on NavComponent view recreation.
        childFragmentManager.findFragmentById(R.id.content_frame)?.let { syncToolbarForFragment(it) }

        ui.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val title = pref.title?.toString().orEmpty()

        @Suppress("DEPRECATION")
        val fragment = childFragmentManager.fragmentFactory
            .instantiate(this::class.java.classLoader!!, pref.fragment!!)
            .apply {
                arguments = Bundle().apply {
                    putAll(pref.extras)
                    putString(BKEY_SCREEN_TITLE, title)
                }
                setTargetFragment(caller, 0)
            }

        // Update toolbar synchronously before commit so the title matches the transition.
        setToolbar(
            title = title,
            subtitle = getString(eu.darken.sdmse.common.R.string.general_settings_title),
        )

        childFragmentManager.beginTransaction().apply {
            replace(R.id.content_frame, fragment)
            addToBackStack(null)
        }.commit()

        return true
    }

    private fun syncToolbarForFragment(f: Fragment) {
        val screenTitle = f.arguments?.getString(BKEY_SCREEN_TITLE)
        if (screenTitle != null) {
            setToolbar(
                title = screenTitle,
                subtitle = getString(eu.darken.sdmse.common.R.string.general_settings_title),
            )
        } else {
            setToolbar(
                title = getString(eu.darken.sdmse.common.R.string.general_settings_title),
                subtitle = null,
            )
        }
    }

    private fun setToolbar(title: String?, subtitle: String?) {
        ui.toolbar.title = title
        ui.toolbar.subtitle = subtitle
    }

    companion object {
        private const val BKEY_SCREEN_TITLE = "preferenceScreenTitle"
    }
}
