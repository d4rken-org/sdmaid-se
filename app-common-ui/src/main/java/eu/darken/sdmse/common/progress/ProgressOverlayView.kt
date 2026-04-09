package eu.darken.sdmse.common.progress

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.CircularProgressIndicator
import eu.darken.sdmse.common.progress.Progress.Count
import eu.darken.sdmse.common.ui.R

class ProgressOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val progress: CircularProgressIndicator
    private val progressText: TextView
    private val primary: TextView
    private val secondary: TextView

    init {
        var expanded = false
        context.obtainStyledAttributes(attrs, R.styleable.ProgressOverlayView).apply {
            try {
                expanded = getBoolean(R.styleable.ProgressOverlayView_expanded, false)
            } finally {
                recycle()
            }
        }

        val layoutRes = if (expanded) {
            R.layout.view_progress_overlay_large
        } else {
            R.layout.view_progress_overlay
        }
        LayoutInflater.from(context).inflate(layoutRes, this, true)

        progress = findViewById(R.id.progress)
        progressText = findViewById(R.id.progress_text)
        primary = findViewById(R.id.primary)
        secondary = findViewById(R.id.secondary)
    }

    fun setProgress(data: Progress.Data?) {
        val wasVisible = isVisible
        isVisible = data != null
        if (data == null) return

        if (!wasVisible) {
            alpha = 0f
            scaleX = 0.94f
            scaleY = 0.94f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(ENTRANCE_ANIM_DURATION_MS)
                .start()
        }

        primary.apply {
            val newText = data.primary.get(context)
            text = newText
            isInvisible = newText.isEmpty()
        }
        secondary.apply {
            val newText = data.secondary.get(context)
            text = newText
            isInvisible = newText.isEmpty()
        }

        progress.apply {
            isGone = data.count is Count.None
            isIndeterminate = true
            when (data.count) {
                is Count.Counter -> {
                    isIndeterminate = data.count.current == 0L
                    progress = data.count.current.toInt()
                    max = data.count.max.toInt()
                }
                is Count.Percent -> {
                    isIndeterminate = data.count.current == 0L
                    progress = data.count.current.toInt()
                    max = data.count.max.toInt()
                }
                is Count.Indeterminate -> {
                    isIndeterminate = true
                }
                is Count.Size -> {}
                is Count.None -> {}
            }
        }
        progressText.apply {
            text = data.count.displayValue(context)
            isGone = data.count is Count.Indeterminate || data.count is Count.None
        }
    }

    companion object {
        private const val ENTRANCE_ANIM_DURATION_MS = 180L
    }
}
