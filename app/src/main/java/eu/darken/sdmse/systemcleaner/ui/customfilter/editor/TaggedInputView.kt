package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import android.content.Context
import android.text.InputFilter
import android.text.TextUtils.TruncateAt
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.EditorInfo
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.children
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.ui.getString
import eu.darken.sdmse.databinding.ViewTaggedInputBinding
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SieveCriterium
import java.io.File

class TaggedInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val ui = ViewTaggedInputBinding.inflate(LayoutInflater.from(context), this, true)
    private val container = ui.tagsContainer
    private val inputLayout = ui.tagInputLayout
    private val input = inputLayout.editText!!

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.TaggedInputView, 0, 0).apply {
            try {
                inputLayout.hint = getString(R.styleable.TaggedInputView_tivTitle)
            } finally {
                recycle()
            }
        }

        input.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if (input.text.toString() == " ") input.text.clear()
            } else {
                if (input.text.isNotEmpty()) input.text.clear()
                else if (input.text.isNullOrEmpty() && container.childCount > 0) input.setText(" ")
            }
            onFocusChange?.invoke(this, hasFocus)
        }
        // Backspace
        input.setOnKeyListener { _, _, event ->
            when {
                container.childCount > 0
                        && event?.action == KeyEvent.ACTION_DOWN
                        && event.keyCode == KeyEvent.KEYCODE_DEL
                        && input.text.isEmpty() -> {
                    val neighbor = container.getChildAt(container.childCount - 1)

                    val unchipped = neighbor as? Chip
                    if (unchipped == null) {
                        log(WARN) { "Expected Chip but got $neighbor" }
                        return@setOnKeyListener false
                    }

                    removeChip(unchipped)
                    input.append(unchipped.text)

                    true
                }

                else -> false
            }
        }
        // Enter
        input.setOnEditorActionListener { _, actionId, event ->
            when {
                actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER -> {
                    val newText = input.text.toString()
                    if (newText.isEmpty()) return@setOnEditorActionListener false
                    addChip(inputTextToChipTag(newText))
                    input.text.clear()
                    true
                }

                else -> true
            }
        }

        if (isInEditMode) {
            (1..3).forEach {
                ChipTag(
                    criterium = NameCriterium(
                        name = "Chip $it",
                        mode = NameCriterium.Mode.Contain()
                    )
                ).run { addChip(this) }
            }
        }
    }

    enum class Type {
        SEGMENTS,
        NAME,
        ;
    }

    var type: Type = Type.SEGMENTS
        set(value) {
            log { "Setting type to $value" }
            inputFilter = when (value) {
                Type.SEGMENTS -> null
                Type.NAME -> InputFilter { source, start, end, _, _, _ ->
                    for (i in start until end) {
                        if (source[i] == File.separatorChar) return@InputFilter ""
                    }
                    null
                }
            }
            field = value
        }
    val currentChipTags: Collection<ChipTag>
        get() = container.children
            .filterIsInstance<Chip>()
            .map { it.tag as ChipTag }
            .toList()

    var onUserAddedTag: ((SieveCriterium) -> Unit)? = null
    var onUserRemovedTag: ((SieveCriterium) -> Unit)? = null

    var onFocusChange: ((TaggedInputView, Boolean) -> Unit)? = null

    private var inputFilter: InputFilter?
        get() = input.filters.firstOrNull()
        set(value) {
            input.filters = value?.let { arrayOf(it) } ?: emptyArray()
        }

    private fun inputTextToChipTag(input: String): ChipTag {
        val criterium = when (type) {
            Type.SEGMENTS -> SegmentCriterium(
                segments = input.toSegs(),
                mode = SegmentCriterium.Mode.Contain(allowPartial = true)
            )

            Type.NAME -> NameCriterium(
                name = input,
                mode = NameCriterium.Mode.Contain()
            )
        }
        return ChipTag(criterium = criterium)
    }

    private fun addChip(chipTag: ChipTag, position: Int? = null, silent: Boolean = false) {
        log { "addChip($chipTag,$position,$silent)" }
        val chip = chipTag.toChip()
        if (position != null) {
            container.addView(chip, position)
        } else {
            container.addView(chip)
        }
        if (!silent) onUserAddedTag?.invoke(chip.toTag().criterium)
    }

    private fun removeChip(chip: Chip, silent: Boolean = false): Int {
        val oldPosition = container.indexOfChild(chip)
        log { "removeChip($chip,$silent) pos=$oldPosition" }
        container.removeView(chip)
        if (!silent) onUserRemovedTag?.invoke(chip.toTag().criterium)
        return oldPosition
    }

    fun setTags(criteria: List<SieveCriterium>) {
        log { "setTags($criteria)" }
        container.children
            .filterIsInstance<Chip>().toList()
            .forEach { removeChip(it, silent = true) }
        criteria
            .map { ChipTag(criterium = it) }
            .forEach { addChip(it, silent = true) }
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

        text = chipTag.value
        ellipsize = when (chipTag.criterium) {
            is NameCriterium -> when (chipTag.criterium.mode) {
                is NameCriterium.Mode.Contain -> TruncateAt.MIDDLE
                is NameCriterium.Mode.End -> TruncateAt.START
                is NameCriterium.Mode.Equal -> TruncateAt.MIDDLE
                is NameCriterium.Mode.Start -> TruncateAt.END
            }

            is SegmentCriterium -> when (chipTag.criterium.mode) {
                is SegmentCriterium.Mode.Ancestor -> TruncateAt.START
                is SegmentCriterium.Mode.Contain -> TruncateAt.MIDDLE
                is SegmentCriterium.Mode.End -> TruncateAt.START
                is SegmentCriterium.Mode.Equal -> TruncateAt.MIDDLE
                is SegmentCriterium.Mode.Start -> TruncateAt.START
            }
        }

        chipIcon = ContextCompat.getDrawable(context, iconRes)
        chipIconSize = context.dpToPx(16f).toFloat()
        chipStartPadding = context.dpToPx(8f).toFloat()

        isCloseIconVisible = true
        isClickable = true
        isCheckable = false

        setOnClickListener {
            removeChip(chip, silent = true)
            addChip(chipTag, silent = true)
        }
        setOnLongClickListener {
            chipTag.showModeSwitcher { newChipTag ->
                val oldPosition = removeChip(chip)
                addChip(newChipTag, position = oldPosition)
            }
            true
        }
        setOnCloseIconClickListener { removeChip(this) }
    }

    private fun ChipTag.showModeSwitcher(callback: (ChipTag) -> Unit) = MaterialAlertDialogBuilder(context).apply {
        val chipTag = this@showModeSwitcher
        setTitle(
            when (chipTag.criterium) {
                is SegmentCriterium -> R.string.systemcleaner_customfilter_editor_segments_matching_mode_label
                is NameCriterium -> R.string.systemcleaner_customfilter_editor_name_matching_mode_label
            }
        )

        val tagTypes = when (chipTag.criterium) {
            is NameCriterium -> listOf(
                NameCriterium.Mode.Start() to R.string.systemcleaner_customfilter_editor_segments_matching_mode_start_label,
                NameCriterium.Mode.Contain() to R.string.systemcleaner_customfilter_editor_segments_matching_mode_contains_label,
                NameCriterium.Mode.End() to R.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label,
                NameCriterium.Mode.Equal() to R.string.systemcleaner_customfilter_editor_segments_matching_mode_equal_label,
            )

            is SegmentCriterium -> listOf(
                SegmentCriterium.Mode.Start(allowPartial = true) to R.string.systemcleaner_customfilter_editor_name_matching_mode_start_label,
                SegmentCriterium.Mode.Contain(allowPartial = true) to R.string.systemcleaner_customfilter_editor_name_matching_mode_contains_label,
                SegmentCriterium.Mode.End(allowPartial = true) to R.string.systemcleaner_customfilter_editor_name_matching_mode_end_label,
                SegmentCriterium.Mode.Equal() to R.string.systemcleaner_customfilter_editor_name_matching_mode_equal_label,
            )
        }
        setSingleChoiceItems(
            tagTypes.map { getString(it.second) }.toTypedArray(),
            tagTypes.indexOfFirst { it.first::class.isInstance(criterium.mode) },
        ) { dialog, which ->
            val newType = tagTypes[which].first
            val newChipTag = chipTag.copy(
                criterium = when (newType) {
                    is NameCriterium.Mode -> (chipTag.criterium as NameCriterium).copy(
                        mode = newType
                    )

                    is SegmentCriterium.Mode -> (chipTag.criterium as SegmentCriterium).copy(
                        mode = newType
                    )
                }
            )
            callback(newChipTag)
            dialog.dismiss()
        }
        setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
    }.show()

    data class ChipTag(
        val criterium: SieveCriterium
    ) {

        val value: String
            get() = when (criterium) {
                is NameCriterium -> criterium.name
                is SegmentCriterium -> criterium.segments.joinSegments()
            }

        @get:StringRes val labelRes: Int
            get() = when (criterium) {
                is NameCriterium -> when (criterium.mode) {
                    is NameCriterium.Mode.Start -> R.string.systemcleaner_customfilter_editor_name_matching_mode_start_label
                    is NameCriterium.Mode.Contain -> R.string.systemcleaner_customfilter_editor_name_matching_mode_contains_label
                    is NameCriterium.Mode.End -> R.string.systemcleaner_customfilter_editor_name_matching_mode_end_label
                    is NameCriterium.Mode.Equal -> R.string.systemcleaner_customfilter_editor_name_matching_mode_equal_label
                }

                is SegmentCriterium -> when (criterium.mode) {
                    is SegmentCriterium.Mode.Start -> R.string.systemcleaner_customfilter_editor_segments_matching_mode_start_label
                    is SegmentCriterium.Mode.Contain -> R.string.systemcleaner_customfilter_editor_segments_matching_mode_contains_label
                    is SegmentCriterium.Mode.End -> R.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label
                    is SegmentCriterium.Mode.Equal -> R.string.systemcleaner_customfilter_editor_segments_matching_mode_equal_label
                    is SegmentCriterium.Mode.Ancestor -> throw IllegalArgumentException("Ancestor not supported")
                }
            }
        @get:DrawableRes val iconRes: Int
            get() = when (criterium) {
                is NameCriterium -> when (criterium.mode) {
                    is NameCriterium.Mode.Contain -> R.drawable.ic_contain_24
                    is NameCriterium.Mode.End -> R.drawable.ic_contain_end_24
                    is NameCriterium.Mode.Equal -> R.drawable.ic_approximately_equal_24
                    is NameCriterium.Mode.Start -> R.drawable.ic_contain_start_24
                }

                is SegmentCriterium -> when (criterium.mode) {
                    is SegmentCriterium.Mode.Contain -> R.drawable.ic_contain_24
                    is SegmentCriterium.Mode.End -> R.drawable.ic_contain_end_24
                    is SegmentCriterium.Mode.Equal -> R.drawable.ic_approximately_equal_24
                    is SegmentCriterium.Mode.Start -> R.drawable.ic_contain_start_24
                    is SegmentCriterium.Mode.Ancestor -> throw IllegalArgumentException("Ancestor not supported")
                }
            }
    }
}