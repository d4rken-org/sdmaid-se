package eu.darken.sdmse.analyzer.ui.storage.device

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerDeviceVhBinding
import kotlin.math.roundToInt


class DeviceStorageItemVH(parent: ViewGroup) :
    DeviceStorageAdapter.BaseVH<DeviceStorageItemVH.Item, AnalyzerDeviceVhBinding>(
        R.layout.analyzer_device_vh,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerDeviceVhBinding.bind(itemView) }

    override val onBindData: AnalyzerDeviceVhBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val storage = item.storage

        primary.text = storage.label.get(context)
        secondary.text = when (storage.type) {
            DeviceStorage.Type.PRIMARY -> getString(R.string.analyzer_storage_type_primary_description)
            DeviceStorage.Type.SECONDARY -> getString(R.string.analyzer_storage_type_secondary_description)
        }
        tertiary.apply {
            isVisible = item.storage.setupIncomplete
            text = getString(R.string.analyzer_storage_content_type_app_setup_incomplete_hint)
        }

        hardwareIcon.setImageResource(
            when (storage.hardware) {
                DeviceStorage.Hardware.BUILT_IN -> R.drawable.ic_chip_24
                DeviceStorage.Hardware.SDCARD -> R.drawable.ic_sd_24
            }
        )

        val formattedUsed = Formatter.formatShortFileSize(context, storage.spaceUsed)
        val formattedTotal = Formatter.formatShortFileSize(context, storage.spaceCapacity)
        capacity.text = "$formattedUsed / $formattedTotal"

        val percentUsed = ((storage.spaceUsed / storage.spaceCapacity.toDouble()) * 100).toInt()
        progress.progress = percentUsed
        graphCaption.text = "$percentUsed%"

        available.apply {
            val formattedFree = Formatter.formatShortFileSize(context, storage.spaceFree)
            text = getQuantityString(
                R.plurals.analyzer_space_available,
                ByteFormatter.stripSizeUnit(formattedFree)?.roundToInt() ?: 1,
                formattedFree
            )
            setTextColor(
                if (percentUsed > 95) {
                    getColorForAttr(android.R.attr.colorError)
                } else {
                    getColorForAttr(android.R.attr.colorControlNormal)
                }
            )
        }

        root.setOnClickListener { item.onItemClicked(item) }
    }

    data class Item(
        val storage: DeviceStorage,
        val onItemClicked: (Item) -> Unit,
    ) : DeviceStorageAdapter.Item {

        override val stableId: Long = storage.id.hashCode().toLong()
    }

}