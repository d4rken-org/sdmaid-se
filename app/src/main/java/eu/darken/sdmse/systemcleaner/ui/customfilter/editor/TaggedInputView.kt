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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.common.ui.getString
import eu.darken.sdmse.databinding.ViewTaggedInputBinding
import java.io.File

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
                if (input.text.isNotEmpty()) {
                    addChip(ChipTag(input.text.toString()).toChip())
                    input.text.clear()
                }
                if (input.text.isNullOrEmpty() && container.childCount > 0) {
                    input.setText(" ")
                }
            }
            onFocusChange?.invoke(this, hasFocus)
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
                    addChip(ChipTag(newText).toChip())
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
            (1..3).forEach { addChip(ChipTag(value = "Test Chip $it").toChip()) }
        }
    }

    var type: Type = Type.SEGMENTS
        set(value) {
            inputFilter = when (value) {
                Type.SEGMENTS -> NAME_INPUT_FILTER
                Type.NAME -> null
            }
            field = value
        }
    val currentChipTags: Collection<ChipTag>
        get() = container.children
            .filterIsInstance<Chip>()
            .map { it.tag as ChipTag }
            .toList()

    private fun addChip(chip: Chip, position: Int? = null, silent: Boolean = false) {
        if (position != null) {
            container.addView(chip, position)
        } else {
            container.addView(chip)
        }
        if (!silent) onUserAddedTag?.invoke(chip.toTag())
    }

    private fun removeChip(chip: Chip, silent: Boolean = false): Int {
        val oldPosition = container.indexOfChild(chip)
        container.removeView(chip)
        if (!silent) onUserRemovedTag?.invoke(chip.toTag())
        return oldPosition
    }

    var onUserAddedTag: ((ChipTag) -> Unit)? = null

    var onUserRemovedTag: ((ChipTag) -> Unit)? = null

    var inputFilter: InputFilter?
        get() = input.filters.firstOrNull()
        set(value) {
            input.filters = value?.let { arrayOf(it) } ?: emptyArray()
        }

    var onFocusChange: ((TaggedInputView, Boolean) -> Unit)? = null

    var allowedChipTagTypes: Set<ChipTag.Mode> = ChipTag.Mode.values().toSet()

    fun setTags(chipTags: List<ChipTag>) {
        container.children.filterIsInstance<Chip>().toList().forEach { removeChip(it, silent = true) }
        chipTags.forEach { addChip(it.toChip(), silent = true) }
    }

    private fun Chip.toTag(): ChipTag = this.tag as ChipTag

    private fun ChipTag.toChip(): Chip = Chip(
        context,
        null,
        com.google.android.material.R.style.Widget_Material3_Chip_Input_Icon_Elevated
    ).apply {
        val chip = this
        val chipTag = this@toChip
        tag = chipTag
        id = ViewCompat.generateViewId()
        this.text = chipTag.value
        chipIcon = ContextCompat.getDrawable(context, iconRes)
        chipIconSize = context.dpToPx(16f).toFloat()
        chipStartPadding = context.dpToPx(8f).toFloat()
        isCloseIconVisible = true
        isClickable = true
        isCheckable = false
        setOnCloseIconClickListener { removeChip(this) }
        setOnLongClickListener {
            MaterialAlertDialogBuilder(context).apply {
                setTitle(
                    when (type) {
                        Type.SEGMENTS -> R.string.systemcleaner_customfilter_editor_segments_matching_mode_label
                        Type.NAME -> R.string.systemcleaner_customfilter_editor_name_matching_mode_label
                    }
                )
                val tagTypes = allowedChipTagTypes.sorted()
                setSingleChoiceItems(
                    tagTypes.map { mode ->
                        when (type) {
                            Type.SEGMENTS -> when (mode) {
                                ChipTag.Mode.START -> R.string.systemcleaner_customfilter_editor_segments_matching_mode_start_label
                                ChipTag.Mode.CONTAINS -> R.string.systemcleaner_customfilter_editor_segments_matching_mode_contains_label
                                ChipTag.Mode.END -> R.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label
                                ChipTag.Mode.MATCH -> R.string.systemcleaner_customfilter_editor_segments_matching_mode_match_label
                            }

                            Type.NAME -> when (mode) {
                                ChipTag.Mode.START -> R.string.systemcleaner_customfilter_editor_name_matching_mode_start_label
                                ChipTag.Mode.CONTAINS -> R.string.systemcleaner_customfilter_editor_name_matching_mode_contains_label
                                ChipTag.Mode.END -> R.string.systemcleaner_customfilter_editor_name_matching_mode_end_label
                                ChipTag.Mode.MATCH -> R.string.systemcleaner_customfilter_editor_name_matching_mode_match_label
                            }
                        }.let { getString(it) }
                    }.toTypedArray(),
                    tagTypes.indexOf(chipTag.mode),
                ) { dialog, which ->
                    val newType = tagTypes[which]
                    val oldPosition = removeChip(chip)
                    addChip(chipTag.copy(mode = newType).toChip(), position = oldPosition)
                    dialog.dismiss()
                }

                setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
            }.show()
            true
        }
    }

    enum class Type {
        SEGMENTS,
        NAME,
        ;
    }

    data class ChipTag(
        val value: String,
        val mode: Mode = Mode.CONTAINS,
    ) {
        @DrawableRes val iconRes: Int = when (mode) {
            Mode.START -> R.drawable.ic_contain_start_24
            Mode.CONTAINS -> R.drawable.ic_contain_24
            Mode.END -> R.drawable.ic_contain_end_24
            Mode.MATCH -> R.drawable.ic_approximately_equal_24
        }

        enum class Mode {
            START,
            CONTAINS,
            END,
            MATCH,
            ;
        }
    }

    companion object {
        private val NAME_INPUT_FILTER = InputFilter { source, start, end, dest, dstart, dend ->
            for (i in start until end) {
                if (source[i] == File.separatorChar) return@InputFilter ""
            }
            null
        }
    }

}