package eu.darken.sdmse.common.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import eu.darken.sdmse.R
import eu.darken.sdmse.common.error.localized
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.databinding.LoadingOverlayViewBinding

@Suppress("ProtectedInFinal")
class LoadingOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding = LoadingOverlayViewBinding.inflate(layoutInflator, this)

    init {
        setMode(Mode.LOADING)
    }

    fun setMode(mode: Mode) {
        binding.animation.setAnimation(mode.animationRes)
        binding.animation.addValueCallback(KeyPath("**"), LottieProperty.COLOR_FILTER) {
            PorterDuffColorFilter(context.getColorForAttr(R.attr.colorOnBackground), PorterDuff.Mode.SRC_ATOP)
        }
        binding.animation.repeatCount = LottieDrawable.INFINITE
        binding.animation.playAnimation()
        binding.primaryText.setText(mode.defaultPrimary)
    }

    fun setPrimaryText(@StringRes stringRes: Int) {
        this.setPrimaryText(context.getString(stringRes))
    }

    fun setPrimaryText(primary: String?) {
        if (primary == null) {
            binding.primaryText.setText(R.string.progress_loading_label)
            return
        }
        binding.primaryText.text = primary
    }

    fun updateWith(error: Throwable?) {
        if (error == null) {
            setMode(Mode.LOADING)
            return
        }
        setMode(Mode.ERROR)
        binding.primaryText.text = error.localized(context).asText()
    }

    var isCancelable: Boolean = false
        set(value) {
            field = value
            binding.cancelButton.setGone(!value)
        }

    fun setOnCancelListener(function: ((View) -> Unit)?) {
        binding.cancelButton.setOnClickListener(function)
    }

    data class Mode(
        @RawRes val animationRes: Int,
        @StringRes val defaultPrimary: Int
    ) {
        companion object {
            val LOADING = Mode(R.raw.anim_loading_box, R.string.progress_loading_label)
            val ERROR = Mode(R.raw.anim_loading_box, R.string.general_error_label)
        }
    }

}