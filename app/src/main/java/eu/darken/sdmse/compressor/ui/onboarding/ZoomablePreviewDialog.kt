package eu.darken.sdmse.compressor.ui.onboarding

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import eu.darken.sdmse.R
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.databinding.CompressorZoomablePreviewDialogBinding

class ZoomablePreviewDialog : DialogFragment() {

    private var _binding: CompressorZoomablePreviewDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.DialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = CompressorZoomablePreviewDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupImmersiveMode()

        val args = requireArguments()
        val label = args.getString(ARG_LABEL)

        binding.label.text = label
        binding.closeAction.setOnClickListener { dismiss() }
        binding.photoView.setOnClickListener { dismiss() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.closeAction) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val layoutParams = v.layoutParams as FrameLayout.LayoutParams
            layoutParams.topMargin = requireContext().dpToPx(16f) + systemBars.top
            v.layoutParams = layoutParams
            insets
        }

        val filePath = args.getString(ARG_FILE_PATH)
        if (filePath != null) {
            val bitmap = BitmapFactory.decodeFile(filePath)
            binding.photoView.setImageBitmap(bitmap)
        } else {
            pendingBitmap?.let { bitmap ->
                binding.photoView.setImageBitmap(bitmap)
            }
        }
    }

    private fun setupImmersiveMode() {
        if (hasApiLevel(30)) {
            WindowCompat.getInsetsController(dialog!!.window!!, requireView()).apply {
                hide(WindowInsetsCompat.Type.statusBars())
                hide(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    override fun onDestroyView() {
        restoreSystemBars()
        _binding = null
        pendingBitmap = null
        super.onDestroyView()
    }

    private fun restoreSystemBars() {
        if (hasApiLevel(30)) {
            WindowCompat.getInsetsController(dialog!!.window!!, requireView()).apply {
                show(WindowInsetsCompat.Type.statusBars())
                show(WindowInsetsCompat.Type.navigationBars())
                @Suppress("DEPRECATION")
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
            }
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.systemUiVisibility = 0
        }
    }

    companion object {
        private const val ARG_FILE_PATH = "file_path"
        private const val ARG_LABEL = "label"

        private var pendingBitmap: Bitmap? = null

        fun newInstance(filePath: String, label: String): ZoomablePreviewDialog {
            return ZoomablePreviewDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE_PATH, filePath)
                    putString(ARG_LABEL, label)
                }
            }
        }

        fun newInstance(bitmap: Bitmap, label: String): ZoomablePreviewDialog {
            pendingBitmap = bitmap
            return ZoomablePreviewDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_LABEL, label)
                }
            }
        }
    }
}
