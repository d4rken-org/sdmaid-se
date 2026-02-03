package eu.darken.sdmse.swiper.ui.swipe

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.databinding.SwiperProgressItemBinding
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem

class ProgressPagerAdapter : ListAdapter<SwipeItem, ProgressPagerAdapter.ViewHolder>(DIFF_CALLBACK) {

    var currentItemIndex: Int = 0
        set(value) {
            val oldValue = field
            field = value
            if (oldValue != value) {
                notifyItemChanged(oldValue, PAYLOAD_CURRENT_STATE)
                notifyItemChanged(value, PAYLOAD_CURRENT_STATE)
            }
        }

    var onItemClick: ((Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = SwiperProgressItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, position == currentItemIndex)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_CURRENT_STATE)) {
            // Partial bind: only update current item highlight, skip image loading
            holder.bindCurrentState(position == currentItemIndex)
        } else {
            // Full bind
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ViewHolder(
        private val binding: SwiperProgressItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                onItemClick?.invoke(bindingAdapterPosition)
            }
        }

        fun bind(item: SwipeItem, position: Int, isCurrent: Boolean) {
            val context = binding.root.context

            // Set item number using original scan position
            binding.itemNumber.text = "#${item.itemIndex + 1}"

            // Load thumbnail
            binding.thumbnail.loadFilePreview(item.lookup) {
                crossfade(true)
                size(64, 64)
            }

            // Highlight current item with elevation change
            bindCurrentState(isCurrent)

            // Decision icon
            when (item.decision) {
                SwipeDecision.KEEP -> {
                    binding.decisionIcon.isVisible = true
                    binding.decisionIcon.setImageResource(R.drawable.ic_heart)
                    ViewCompat.setBackgroundTintList(
                        binding.decisionIcon,
                        ColorStateList.valueOf(context.getColorForAttr(androidx.appcompat.R.attr.colorPrimary)),
                    )
                    binding.decisionIcon.setColorFilter(
                        context.getColorForAttr(com.google.android.material.R.attr.colorOnPrimary),
                    )
                }
                SwipeDecision.DELETE -> {
                    binding.decisionIcon.isVisible = true
                    binding.decisionIcon.setImageResource(R.drawable.ic_delete)
                    ViewCompat.setBackgroundTintList(
                        binding.decisionIcon,
                        ColorStateList.valueOf(context.getColorForAttr(androidx.appcompat.R.attr.colorError)),
                    )
                    binding.decisionIcon.setColorFilter(
                        context.getColorForAttr(com.google.android.material.R.attr.colorOnError),
                    )
                }
                else -> {
                    binding.decisionIcon.isVisible = false
                }
            }
        }

        fun bindCurrentState(isCurrent: Boolean) {
            binding.currentIndicator.isVisible = isCurrent
        }
    }

    companion object {
        private const val PAYLOAD_CURRENT_STATE = "current_state"
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SwipeItem>() {
            override fun areItemsTheSame(oldItem: SwipeItem, newItem: SwipeItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: SwipeItem, newItem: SwipeItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
