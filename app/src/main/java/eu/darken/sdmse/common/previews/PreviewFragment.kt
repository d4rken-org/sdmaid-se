package eu.darken.sdmse.common.previews

import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import coil.load
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

        vm.state.observe2(ui) { state ->
            previewImage.apply {
                isGone = state.preview == null
                load(state.preview)
            }

            previewTitle.text = state.preview?.path
            previewSubtitle.text = state.preview?.let { Formatter.formatFileSize(requireContext(), it.size) }

            previewInfoContainer.isGone = state.preview == null
            progress.isGone = state.progress == null
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
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
            }
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.systemUiVisibility = 0
        }
        super.onDestroyView()
    }

}
