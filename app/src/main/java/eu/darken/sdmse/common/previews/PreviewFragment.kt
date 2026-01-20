package eu.darken.sdmse.common.previews

import android.os.Bundle
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.navigation.popBackStack
import eu.darken.sdmse.common.uix.DialogFragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.PreviewFragmentBinding

@AndroidEntryPoint
class PreviewFragment : DialogFragment3(R.layout.preview_fragment) {

    override val vm: PreviewViewModel by viewModels()
    override val ui: PreviewFragmentBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setStyle(STYLE_NO_FRAME, R.style.DialogTheme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (hasApiLevel(30)) {
            WindowCompat.getInsetsController(dialog!!.window!!, view).apply {
                // Hide both the status bar and the navigation bar
                hide(WindowInsetsCompat.Type.statusBars())
                hide(WindowInsetsCompat.Type.navigationBars())
                // Enable immersive mode
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN // Hide the status bar
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // Hide the navigation bar
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY // Enable immersive mode
                    )
        }

        ui.backAction.setOnClickListener { popBackStack() }
        ui.nextAction.setOnClickListener { vm.next() }
        ui.previousAction.setOnClickListener { vm.previous() }

        val pagerAdapter = PreviewAdapter(requireActivity(), childFragmentManager)
        ui.viewpager.apply {
            adapter = pagerAdapter
            addOnPageChangeListener(object : OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(position: Int) {
                    vm.onNewPage(position)
                }

                override fun onPageScrollStateChanged(state: Int) {}
            })
        }

        vm.state.observe2(ui) { state ->
            viewpager.isGone = state.preview == null
            nextAction.isGone = state.previews.isNullOrEmpty() || state.previews.size < 2
            previousAction.isGone = state.previews.isNullOrEmpty() || state.previews.size < 2

            headerTitle.text = "${state.position + 1} / ${state.previews?.size ?: 1}"

            pagerAdapter.apply {
                setData(state.previews)
                notifyDataSetChanged()
            }
            viewpager.setCurrentItem(state.position, false)
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is PreviewEvents.ConfirmDeletion -> {
                    // TODO
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        if (hasApiLevel(30)) {
            WindowCompat.getInsetsController(dialog!!.window!!, requireView()).apply {
                // Hide both the status bar and the navigation bar
                show(WindowInsetsCompat.Type.statusBars())
                show(WindowInsetsCompat.Type.navigationBars())
                // Enable immersive mode
                @Suppress("DEPRECATION")
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
            }
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.systemUiVisibility = 0
        }
        super.onDestroyView()
    }

}
