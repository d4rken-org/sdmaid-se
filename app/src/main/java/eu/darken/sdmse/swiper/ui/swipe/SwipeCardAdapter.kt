package eu.darken.sdmse.swiper.ui.swipe

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.databinding.SwiperCardItemBinding
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem

class SwipeCardAdapter : ListAdapter<SwipeItem, SwipeCardAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = SwiperCardItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: SwiperCardItemBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SwipeItem) {
            binding.previewImage.loadFilePreview(item.lookup) {
                listener(
                    onSuccess = { _, _ ->
                        binding.previewImage.isVisible = true
                        binding.noPreviewContainer.isVisible = false
                    },
                    onError = { _, _ ->
                        binding.previewImage.isVisible = false
                        binding.noPreviewContainer.isVisible = true
                    },
                )
            }

            // Show stamp indicator for already-decided items
            when (item.decision) {
                SwipeDecision.KEEP -> {
                    binding.stampKeep.alpha = 0.3f
                    binding.stampKeep.scaleX = 1f
                    binding.stampKeep.scaleY = 1f
                    binding.stampDelete.alpha = 0f
                }
                SwipeDecision.DELETE -> {
                    binding.stampDelete.alpha = 0.3f
                    binding.stampDelete.scaleX = 1f
                    binding.stampDelete.scaleY = 1f
                    binding.stampKeep.alpha = 0f
                }
                else -> {
                    binding.stampKeep.alpha = 0f
                    binding.stampDelete.alpha = 0f
                }
            }
        }
    }

    companion object {
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
