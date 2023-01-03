package eu.darken.sdmse.common.upgrade

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface UpgradeRepo {
    val mainWebsite: String

    val upgradeInfo: Flow<Info>

    interface Info {
        val type: Type

        val isPro: Boolean

        val upgradedAt: Instant?
    }

    enum class Type {
        GPLAY,
        FOSS
    }
}