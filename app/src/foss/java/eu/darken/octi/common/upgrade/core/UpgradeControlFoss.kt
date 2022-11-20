package eu.darken.sdmse.common.upgrade.core

import android.app.Activity
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeControlFoss @Inject constructor(
    private val fossCache: FossCache,
    private val webpageTool: WebpageTool,
) : UpgradeRepo {

    override val upgradeInfo: Flow<UpgradeRepo.Info> = fossCache.upgrade.flow.map { data ->
        if (data == null) {
            Info()
        } else {
            Info(
                isPro = true,
                upgradedAt = data.upgradedAt,
                upgradeReason = data.reason
            )
        }
    }

    override fun launchBillingFlow(activity: Activity) {
        MaterialAlertDialogBuilder(activity).apply {
            setIcon(R.drawable.ic_heart)
            setTitle(R.string.upgrades_upgrade_octi_title)
            setMessage(R.string.upgrades_upgrade_octi_body)
            setPositiveButton(R.string.foss_upgrade_donate_label) { _, _ ->
                fossCache.upgrade.valueBlocking = FossUpgrade(
                    upgradedAt = Instant.now(),
                    reason = FossUpgrade.Reason.DONATED
                )
                webpageTool.open("https://github.com/sponsors/d4rken")
                Toast.makeText(activity, R.string.general_thank_you_label, Toast.LENGTH_SHORT).show()
            }
            setNegativeButton(R.string.foss_upgrade_alreadydonated_label) { _, _ ->
                fossCache.upgrade.valueBlocking = FossUpgrade(
                    upgradedAt = Instant.now(),
                    reason = FossUpgrade.Reason.ALREADY_DONATED
                )
                Toast.makeText(activity, R.string.general_thank_you_label, Toast.LENGTH_SHORT).show()
            }
            setNeutralButton(R.string.foss_upgrade_no_money_label) { _, _ ->
                fossCache.upgrade.valueBlocking = FossUpgrade(
                    upgradedAt = Instant.now(),
                    reason = FossUpgrade.Reason.NO_MONEY
                )
                Toast.makeText(activity, "¯\\_(ツ)_/¯", Toast.LENGTH_SHORT).show()
            }
        }.show()
    }

    data class Info(
        override val isPro: Boolean = false,
        override val upgradedAt: Instant? = null,
        val upgradeReason: FossUpgrade.Reason? = null,
    ) : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS
    }

}