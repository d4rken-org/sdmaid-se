package eu.darken.sdmse.analyzer.ui.storage.device

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerDeviceStorageItemBinding


class DeviceStorageItemVH(parent: ViewGroup) :
    DeviceStorageAdapter.BaseVH<DeviceStorageItemVH.Item, AnalyzerDeviceStorageItemBinding>(
        R.layout.analyzer_device_storage_item,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerDeviceStorageItemBinding.bind(itemView) }

    override val onBindData: AnalyzerDeviceStorageItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val storage = item.storage

        primary.text = storage.label.get(context)
        secondary.text = when (storage.type) {
            DeviceStorage.Type.PRIMARY -> getString(R.string.analyzer_storage_type_primary_description)
            DeviceStorage.Type.SECONDARY -> getString(R.string.analyzer_storage_type_secondary_description)
        }

        hardwareIcon.setImageResource(
            when (storage.hardware) {
                DeviceStorage.Hardware.BUILT_IN -> R.drawable.ic_chip_24
                DeviceStorage.Hardware.SDCARD -> R.drawable.ic_sd_24
            }
        )

        available.apply {
            val formattedFree = Formatter.formatShortFileSize(context, storage.spaceFree)
            text = getString(R.string.analyzer_space_available, formattedFree)
            setTextColor(
                if (storage.spaceFree < 1024L * 1024L * 512) {
                    getColorForAttr(android.R.attr.colorError)
                } else {
                    getColorForAttr(android.R.attr.colorControlNormal)
                }
            )
        }

        val formattedUsed = Formatter.formatShortFileSize(context, storage.spaceUsed)
        val formattedTotal = Formatter.formatShortFileSize(context, storage.spaceCapacity)
        capacity.text = "$formattedUsed / $formattedTotal"

        val percentUsed = ((storage.spaceUsed / storage.spaceCapacity.toDouble()) * 100).toInt()
        progress.progress = percentUsed
        graphCaption.text = "$percentUsed%"

        root.setOnClickListener { item.onItemClicked(item) }
    }

    data class Item(
        val storage: DeviceStorage,
        val onItemClicked: (Item) -> Unit,
    ) : DeviceStorageAdapter.Item {

        override val stableId: Long = storage.id.hashCode().toLong()
    }

}