package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import android.content.Context
import android.text.InputFilter
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.EditorInfo
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.children
import com.google.android.material.chip.Chip
import eu.darken.sdmse.R
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.databinding.ViewTaggedInputBinding

class TaggedInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val ui = ViewTaggedInputBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )
    private val container = ui.tagsContainer
    private val inputLayout = ui.tagInputLayout
    private val input = ui.tagInputLayout.editText!!

    init {
        input.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if (input.text.toString() == " ") input.text.clear()
            } else {
                if (input.text.isNullOrEmpty() && container.childCount > 0) input.setText(" ")
            }
        }
        input.setOnKeyListener { _, _, event ->
            when {
                event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL && container.childCount > 0 -> {
                    (container.getChildAt(container.childCount - 1) as? Chip)?.let {
                        input.append(it.text)
                        removeChip(it)
                    }
                    true
                }

                else -> false
            }
        }
        input.setOnEditorActionListener { _, actionId, event ->
            when {
                actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER -> {
                    val newText = input.text.toString()
                    if (newText.isEmpty()) return@setOnEditorActionListener true
                    addChip(Tag(newText).toChip())
                    input.text.clear()
                    true
                }

                else -> true
            }
        }

        context.theme.obtainStyledAttributes(attrs, R.styleable.TaggedInputView, 0, 0).apply {
            try {
                ui.tagInputLayout.hint = getString(R.styleable.TaggedInputView_tivTitle)
            } finally {
                recycle()
            }
        }

        if (isInEditMode) {
            (1..3).forEach { addChip(Tag(value = "Test Chip $it").toChip()) }
        }
    }

    val currentTags: Collection<Tag>
        get() = container.children
            .filterIsInstance<Chip>()
            .map { Tag(it.text.toString()) }
            .toList()

    private fun addChip(chip: Chip) {
        container.addView(chip)
        onUserAddedTag?.invoke(chip.toTag())
    }

    private fun removeChip(chip: Chip) {
        container.removeView(chip)
        onUserRemovedTag?.invoke(chip.toTag())
    }

    var onUserAddedTag: ((Tag) -> Unit)? = null

    var onUserRemovedTag: ((Tag) -> Unit)? = null

    var inputFilter: InputFilter?
        get() = input.filters.firstOrNull()
        set(value) {
            input.filters = arrayOf(value)
        }

    fun setTags(tags: List<Tag>) {
        container.children.filterIsInstance<Chip>().toList().forEach { container.removeView(it) }
        tags.forEach { addChip(it.toChip()) }
    }

    private fun Chip.toTag() = Tag(value = text.toString())

    private fun Tag.toChip() = Chip(
        context,
        null,
        com.google.android.material.R.style.Widget_Material3_Chip_Input_Icon_Elevated
    ).apply {
        id = ViewCompat.generateViewId()
        this.text = this@toChip.value
        chipIcon = ContextCompat.getDrawable(context, iconRes)
        chipIconSize = context.dpToPx(16f).toFloat()
        chipStartPadding = context.dpToPx(8f).toFloat()
        isCloseIconVisible = true
        isClickable = true
        isCheckable = false
        setOnCloseIconClickListener { removeChip(this) }
    }

    data class Tag(
        val value: String,
        @DrawableRes val iconRes: Int = R.drawable.ic_folder_search_24
    )

}