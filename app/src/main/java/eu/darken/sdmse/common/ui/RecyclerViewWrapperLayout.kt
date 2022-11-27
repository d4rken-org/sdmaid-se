package eu.darken.sdmse.common.ui

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.recyclerview.widget.RecyclerView
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.databinding.ViewRecyclerviewWrapperLayoutBinding
import timber.log.Timber

@Suppress("ProtectedInFinal")
class RecyclerViewWrapperLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val ui = ViewRecyclerviewWrapperLayoutBinding.inflate(layoutInflator, this)

    protected val recyclerView: RecyclerView by lazy {
        ui.recyclerviewContainer.getChildAt(0) as? RecyclerView
            ?: throw IllegalArgumentException("No RecyclerView found")
    }

    protected var currentAdapter: RecyclerView.Adapter<*>? = null
    protected lateinit var state: State
    private val dataListener = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() =
            refresh(currentAdapter?.itemCount ?: -1)

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) =
            refresh(currentAdapter?.itemCount ?: itemCount)

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) =
            refresh(currentAdapter?.itemCount ?: itemCount)

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) =
            refresh((currentAdapter?.itemCount ?: 0) + itemCount)

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) =
            refresh((currentAdapter?.itemCount ?: 0) - itemCount)

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
            refresh(currentAdapter?.itemCount ?: itemCount)

        override fun onStateRestorationPolicyChanged() =
            refresh(currentAdapter?.itemCount ?: -1)

        private fun refresh(itemCount: Int) {
            state = state.copy(dataCount = itemCount)

            if (state.isPreFirstChange && state.isLoading && state.consumeFirstLoading) {
                state = state.copy(
                    isLoading = false,
                    consumeFirstLoading = false,
                    isPreFirstChange = false
                )
            }

            postDelayed(100L) {
                updateUI()
            }

            if (state.isPreFirstData && state.dataCount > 0) {
                state = state.copy(isPreFirstData = false)
            }
        }
    }

    var loadingBehavior: (State) -> Boolean = {
        it.isLoading
    }

    var explanationBehavior: (State) -> Boolean = {
        it.hasExplanation && it.isPreFirstData
            && !it.isLoading
            && it.dataCount == 0
    }

    var emptyBehavior: (State) -> Boolean = {
        (!it.hasExplanation || !it.isPreFirstData)
            && !it.isLoading
            && it.dataCount == 0
    }

    init {
        lateinit var typedArray: TypedArray
        try {
            typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.RecyclerViewWrapperLayout)

            val loadingShow = typedArray.getBoolean(R.styleable.RecyclerViewWrapperLayout_rvwLoading, false)
            val consumeFirstLoading =
                typedArray.getBoolean(R.styleable.RecyclerViewWrapperLayout_rvwLoadingUntilFirstChange, false)

            val explanationIcon = typedArray.getDrawableRes(R.styleable.RecyclerViewWrapperLayout_rvwExplanationIcon)
            if (explanationIcon != null) ui.explanationIcon.setImageResource(explanationIcon)

            val explanationText = typedArray.getStringOrRef(R.styleable.RecyclerViewWrapperLayout_rvwExplanationText)
            if (explanationText != null) ui.explanationText.text = explanationText

            val emptyIcon = typedArray.getDrawableRes(R.styleable.RecyclerViewWrapperLayout_rvwEmptyIcon)
            if (emptyIcon != null) ui.emptyIcon.setImageResource(emptyIcon)

            val emptyText = typedArray.getStringOrRef(R.styleable.RecyclerViewWrapperLayout_rvwEmptyText)
            if (emptyText != null) {
                ui.emptyText.text = emptyText
            } else {
                ui.emptyText.setText(R.string.empty_list_msg)
            }

            state = State(
                hasExplanation = explanationText != null,
                isLoading = loadingShow,
                consumeFirstLoading = consumeFirstLoading
            )
        } finally {
            typedArray.recycle()
        }
    }

    override fun onFinishInflate() {
        updateUI()
        super.onFinishInflate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (currentAdapter != recyclerView.adapter) {
            Timber.v("Updating tracked adapter")
            currentAdapter?.unregisterAdapterDataObserver(dataListener)
            currentAdapter = recyclerView.adapter?.also { it.registerAdapterDataObserver(dataListener) }
            dataListener.onChanged()
        }
        super.onLayout(changed, left, top, right, bottom)
    }

    protected fun updateUI() {
        log { "updateStates(): $state" }
        ui.apply {
            loadingOverlay.isInvisible = !loadingBehavior(state)
            explanationContainer.isInvisible = !explanationBehavior(state)
            emptyContainer.isInvisible = !emptyBehavior(state)

            recyclerviewContainer.isInvisible =
                loadingOverlay.isVisible || explanationContainer.isVisible || emptyContainer.isVisible
        }
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (ROOT_IDS.contains(child.id)) {
            super.addView(child, index, params)
        } else {
            ui.recyclerviewContainer.addView(child, index, params)
        }
    }

    fun setEmptyState(@DrawableRes iconRes: Int? = null, @StringRes stringRes: Int? = null) {
        if (iconRes != null) ui.emptyIcon.setImageResource(iconRes)
        if (stringRes != null) ui.emptyText.setText(stringRes)
    }

    fun updateLoadingState(isLoading: Boolean) {
        state = state.copy(isLoading = isLoading)
        updateUI()
    }

    fun setLoadingText(loadingText: String?) = ui.loadingOverlay.setPrimaryText(loadingText)

    fun setLoadingText(@StringRes loadingRes: Int) = ui.loadingOverlay.setPrimaryText(loadingRes)

    data class State(
        val isPreFirstData: Boolean = true,
        val isPreFirstChange: Boolean = true,
        val isLoading: Boolean,
        val consumeFirstLoading: Boolean,
        val hasExplanation: Boolean,
        val dataCount: Int = 0
    )

    companion object {
        private val ROOT_IDS = listOf(
            R.id.recyclerview_container,
            R.id.explanation_container,
            R.id.empty_container,
            R.id.loading_overlay
        )
    }
}

