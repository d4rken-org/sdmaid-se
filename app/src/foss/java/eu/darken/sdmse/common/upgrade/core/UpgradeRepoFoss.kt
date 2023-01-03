package eu.darken.sdmse.common.upgrade.core

import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeRepoFoss @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val fossCache: FossCache,
    private val webpageTool: WebpageTool,
) : UpgradeRepo {

    override val mainWebsite: String = SITE

    override val upgradeInfo: Flow<UpgradeRepo.Info> = fossCache.upgrade.flow
        .map { data ->
            if (data == null) {
                Info()
            } else {
                Info(
                    isPro = true,
                    upgradedAt = data.upgradedAt,
                    fossUpgradeType = data.upgradeType,
                )
            }
        }
        .setupCommonEventHandlers(TAG) { "upgradeInfo" }

    fun launchGithubSponsorsUpgrade() = appScope.launch {
        log(TAG) { "launchGithubSponsorsUpgrade()" }
        fossCache.upgrade.valueBlocking = FossUpgrade(
            upgradedAt = Instant.now(),
            upgradeType = FossUpgrade.Type.GITHUB_SPONSORS
        )
        webpageTool.open(mainWebsite)
    }

    data class Info(
        override val isPro: Boolean = false,
        override val upgradedAt: Instant? = null,
        val fossUpgradeType: FossUpgrade.Type? = null,
    ) : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS
    }

    companion object {
        private const val SITE = "https://github.com/sponsors/d4rken"
        private val TAG = logTag("Upgrade", "Foss", "Repo")
    }
}