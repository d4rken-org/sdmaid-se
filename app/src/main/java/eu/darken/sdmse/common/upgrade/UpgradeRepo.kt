package eu.darken.sdmse.common.upgrade

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface UpgradeRepo {
    val upgradeInfo: Flow<Info>

    fun launchBillingFlow(activity: Activity)

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