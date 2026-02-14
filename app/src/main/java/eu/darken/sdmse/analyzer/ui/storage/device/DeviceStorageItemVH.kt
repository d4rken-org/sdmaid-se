package eu.darken.sdmse.analyzer.ui.storage.device

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerDeviceVhBinding
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import kotlin.math.absoluteValue
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

        identifier.apply {
            val internalId = storage.id.internalId
            isInvisible = internalId == null
            text = internalId
        }

        secondary.text = when (storage.type) {
            DeviceStorage.Type.PRIMARY -> getString(R.string.analyzer_storage_type_primary_description)
            DeviceStorage.Type.SECONDARY -> getString(R.string.analyzer_storage_type_secondary_description)
            DeviceStorage.Type.PORTABLE -> getString(R.string.analyzer_storage_type_tertiary_description)
        }
        tertiary.apply {
            isVisible = item.storage.setupIncomplete
            text = getString(R.string.analyzer_storage_content_type_app_setup_incomplete_hint)
        }

        hardwareIcon.setImageResource(
            when (storage.hardware) {
                DeviceStorage.Hardware.BUILT_IN -> R.drawable.ic_chip_24
                DeviceStorage.Hardware.SDCARD -> R.drawable.ic_sd_24
                DeviceStorage.Hardware.USB -> R.drawable.ic_usb_24
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

        val snapshots = item.snapshots
        trendContainer.isVisible = snapshots.isNotEmpty()
        trendDelta.isVisible = snapshots.size >= 2

        if (snapshots.isNotEmpty()) {
            trendChart.setData(snapshots)
        }

        if (snapshots.size >= 2) {
            val oldest = snapshots.first().let { it.spaceCapacity - it.spaceFree }
            val newest = snapshots.last().let { it.spaceCapacity - it.spaceFree }
            val delta = newest - oldest
            val absDelta = Formatter.formatShortFileSize(context, delta.absoluteValue)
            val signedDelta = when {
                delta > 0 -> "+$absDelta"
                delta < 0 -> "-$absDelta"
                else -> absDelta
            }
            trendDelta.text = getString(R.string.analyzer_storage_trend_delta_in_7d, signedDelta)
            trendDelta.setTextColor(
                when {
                    delta > 0 -> getColorForAttr(android.R.attr.colorError)
                    delta < 0 -> getColorForAttr(androidx.appcompat.R.attr.colorPrimary)
                    else -> getColorForAttr(android.R.attr.textColorSecondary)
                }
            )
        }

        trendContainer.setOnClickListener {
            item.onTrendClicked?.invoke(item)
        }
        trendContainer.setOnLongClickListener {
            item.onTrendClicked?.invoke(item)
            true
        }
    }

    data class Item(
        val storage: DeviceStorage,
        val snapshots: List<SpaceSnapshotEntity>,
        val isPro: Boolean,
        val onItemClicked: (Item) -> Unit,
        val onTrendClicked: ((Item) -> Unit)?,
    ) : DeviceStorageAdapter.Item {

        override val stableId: Long = storage.id.hashCode().toLong()
    }

}
