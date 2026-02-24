package eu.darken.sdmse.common.upgrade

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface UpgradeRepo {
    val storeSite: String
    val upgradeSite: String
    val betaSite: String

    val upgradeInfo: Flow<Info>

    suspend fun refresh()

    interface Info {
        val type: Type

        val isPro: Boolean

        val upgradedAt: Instant?

        val error: Throwable?
    }

    enum class Type {
        GPLAY,
        FOSS
    }
}