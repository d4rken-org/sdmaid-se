package eu.darken.sdmse.squeezer.ui.onboarding

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.request.CachePolicy
import eu.darken.sdmse.R
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.databinding.SqueezerComparisonDialogBinding
import eu.darken.sdmse.squeezer.core.CompressibleImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ComparisonDialog : DialogFragment() {

    private var _binding: SqueezerComparisonDialogBinding? = null
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
        _binding = SqueezerComparisonDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupImmersiveMode()

        val args = requireArguments()
        val imagePath = args.getString(ARG_IMAGE_PATH)!!
        val quality = args.getInt(ARG_QUALITY)
        val isWebp = args.getBoolean(ARG_IS_WEBP)

        binding.originalLabel.text = getString(R.string.squeezer_onboarding_original_label)
        binding.compressedLabel.text = "${getString(R.string.squeezer_onboarding_compressed_label)} ($quality%)"

        binding.closeAction.setOnClickListener { dismiss() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.closeAction) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val layoutParams = v.layoutParams as FrameLayout.LayoutParams
            layoutParams.topMargin = requireContext().dpToPx(56f) + systemBars.top
            v.layoutParams = layoutParams
            insets
        }

        binding.originalImage.load(File(imagePath)) {
            memoryCachePolicy(CachePolicy.DISABLED)
            diskCachePolicy(CachePolicy.DISABLED)
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = BitmapSampler.decodeSampledBitmap(File(imagePath)) ?: return@launch
            try {
                val mimeType = if (isWebp) CompressibleImage.MIME_TYPE_WEBP else CompressibleImage.MIME_TYPE_JPEG
                val format = CompressibleImage.compressFormat(mimeType)
                val baos = ByteArrayOutputStream()
                bitmap.compress(format, quality, baos)
                bitmap.recycle()

                val previewDir = File(requireContext().cacheDir, "squeezer_preview")
                previewDir.mkdirs()
                val extension = if (isWebp) "webp" else "jpg"
                val compressedFile = File(previewDir, "compressed_q${quality}.$extension")
                FileOutputStream(compressedFile).use { it.write(baos.toByteArray()) }

                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.compressedImage.load(compressedFile) {
                        memoryCachePolicy(CachePolicy.DISABLED)
                        diskCachePolicy(CachePolicy.DISABLED)
                    }
                }
            } catch (_: Exception) {
                bitmap.recycle()
            }
        }

        // Synchronized zoom/pan: forward touch events between views
        binding.originalImage.setOnTouchListener { _, event ->
            val cloned = MotionEvent.obtain(event)
            binding.compressedImage.onTouchEvent(cloned)
            cloned.recycle()
            false
        }
        binding.compressedImage.setOnTouchListener { _, event ->
            val cloned = MotionEvent.obtain(event)
            binding.originalImage.onTouchEvent(cloned)
            cloned.recycle()
            false
        }
    }

    private fun setupImmersiveMode() {
        val window = dialog?.window ?: return
        if (hasApiLevel(30)) {
            WindowCompat.getInsetsController(window, requireView()).apply {
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

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle.EMPTY)
    }

    override fun onDestroyView() {
        restoreSystemBars()
        _binding = null
        val cacheDir = requireContext().cacheDir
        lifecycleScope.launch(Dispatchers.IO) {
            File(cacheDir, "squeezer_preview").deleteRecursively()
        }
        super.onDestroyView()
    }

    private fun restoreSystemBars() {
        val window = dialog?.window ?: return
        if (hasApiLevel(30)) {
            WindowCompat.getInsetsController(window, requireView()).apply {
                show(WindowInsetsCompat.Type.statusBars())
                show(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.systemUiVisibility = 0
        }
    }

    companion object {
        const val REQUEST_KEY = "comparison_dismissed"

        private const val ARG_IMAGE_PATH = "image_path"
        private const val ARG_QUALITY = "quality"
        private const val ARG_IS_WEBP = "is_webp"

        fun newInstance(
            imagePath: String,
            quality: Int,
            isWebp: Boolean,
        ): ComparisonDialog {
            return ComparisonDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_PATH, imagePath)
                    putInt(ARG_QUALITY, quality)
                    putBoolean(ARG_IS_WEBP, isWebp)
                }
            }
        }

    }
}
