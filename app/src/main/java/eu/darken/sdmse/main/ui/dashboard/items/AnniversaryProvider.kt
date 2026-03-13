package eu.darken.sdmse.main.ui.dashboard.items

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.CurriculumVitae
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.stats.core.StatsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

class AnniversaryProvider @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val curriculumVitae: CurriculumVitae,
    private val generalSettings: GeneralSettings,
    private val upgradeRepo: UpgradeRepo,
    private val statsRepo: StatsRepo,
) {

    val item: Flow<AnniversaryCardVH.Item?> = combine(
        generalSettings.anniversaryDismissedYear.flow,
        curriculumVitae.installedAt,
        upgradeRepo.upgradeInfo,
    ) { dismissedYear, installedAt, upgradeInfo ->
        if (!upgradeInfo.isPro) {
            log(TAG, VERBOSE) { "User is not PRO, skipping anniverary check." }
            return@combine null
        }

        val currentYear = LocalDate.now().year
        if (dismissedYear == currentYear) {
            log(TAG, VERBOSE) { "Anniveray check already dismissed for this year." }
            return@combine null
        }

        if (!curriculumVitae.isAnniversary()) return@combine null

        val years = curriculumVitae.getYearsSinceInstall()

        log(TAG) { "Anniversary detected! Years: $years" }

        val spaceFreed = Formatter.formatShortFileSize(context, statsRepo.state.first().totalSpaceFreed)

        AnniversaryCardVH.Item(
            years = years,
            installDate = installedAt,
            spaceFreed = spaceFreed,
            onShare = { yearsCount ->
                val shareText = context.resources.getQuantityString(
                    R.plurals.anniversary_share_text,
                    yearsCount,
                    yearsCount, spaceFreed, upgradeRepo.storeSite,
                )

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }

                Intent.createChooser(shareIntent, context.getString(R.string.anniversary_share_title)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(this)
                }
            },
            onDismiss = {
                appScope.launch { generalSettings.anniversaryDismissedYear.value(currentYear) }
            }
        )
    }

    companion object {
        private val TAG = logTag("Dashboard", "AnniversaryProvider")
    }
}