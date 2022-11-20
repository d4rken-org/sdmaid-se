package eu.darken.sdmse.common.upgrade.core

import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.upgrade.core.data.AvailableSku
import eu.darken.sdmse.common.upgrade.core.data.Sku

enum class OctiSku constructor(override val sku: Sku) : AvailableSku {
    PRO_UPGRADE(Sku("${BuildConfigWrap.APPLICATION_ID}.iap.upgrade.pro"))
}