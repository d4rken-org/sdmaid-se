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

        /**
         * Whether this Info reflects a real entitlement lookup (or a definitive can't-reach-Play
         * outcome). Settledness rides each emission instead of a parallel flow, so it can never
         * be observed out of step with the ownership data it describes. On GPlay the seed emitted
         * before the first billing result after process start is unsettled (and reports non-Pro
         * even for paying users); FOSS reads a local cache and is settled from the first
         * emission. See `isProForUi` for the gate that uses this.
         */
        val isSettled: Boolean

        val upgradedAt: Instant?

        val error: Throwable?
    }

    enum class Type {
        GPLAY,
        FOSS
    }
}