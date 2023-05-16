package eu.darken.sdmse.analyzer.ui.storage.devices

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.storage.DeviceStorage
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerStorageDevicesItemBinding


class StorageDevicesItemVH(parent: ViewGroup) :
    StorageDevicesAdapter.BaseVH<StorageDevicesItemVH.Item, AnalyzerStorageDevicesItemBinding>(
        R.layout.analyzer_storage_devices_item,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerStorageDevicesItemBinding.bind(itemView) }

    override val onBindData: AnalyzerStorageDevicesItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val storage = item.storage

        primary.text = storage.label.get(context)
        secondary.text = storage.description.get(context)

        hardwareIcon.setImageResource(
            when (storage.hardwareType) {
                DeviceStorage.HardwareType.BUILT_IN -> R.drawable.ic_chip_24
                DeviceStorage.HardwareType.SDCARD -> R.drawable.ic_sd_24
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
    ) : StorageDevicesAdapter.Item {

        override val stableId: Long = storage.id.hashCode().toLong()
    }

}