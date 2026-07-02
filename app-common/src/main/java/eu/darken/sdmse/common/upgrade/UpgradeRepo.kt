package eu.darken.sdmse.common.upgrade

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface UpgradeRepo {
    val storeSite: String
    val upgradeSite: String
    val betaSite: String

    val upgradeInfo: Flow<Info>

    /**
     * Whether [upgradeInfo] reflects a real entitlement lookup yet. On GPlay this is `false`
     * until the first billing result arrives after process start (during that window
     * [upgradeInfo] reports non-Pro even for paying users); FOSS reads a local cache and is
     * always settled. See `isProForUi` for the gate that uses this.
     */
    val isSettled: Flow<Boolean>

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