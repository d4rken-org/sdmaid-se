package eu.darken.sdmse.main.ui.dashboard

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.main.ui.dashboard.cards.AnniversaryDashboardCardItem
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class AnniversaryProvider @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val curriculumVitae: CurriculumVitae,
    private val generalSettings: GeneralSettings,
    private val upgradeRepo: UpgradeRepo,
    private val statsRepo: StatsRepo,
) {

    val item: Flow<AnniversaryDashboardCardItem?> = combine(
        generalSettings.anniversaryDismissedOrdinal.flow,
        curriculumVitae.installedAt,
        upgradeRepo.upgradeInfo,
    ) { dismissedOrdinal, installedAt, upgradeInfo ->
        buildItem(dismissedOrdinal, installedAt, upgradeInfo)
    }

    internal suspend fun buildItem(
        dismissedOrdinal: Int?,
        installedAt: Instant,
        upgradeInfo: UpgradeRepo.Info,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): AnniversaryDashboardCardItem? {
        if (!upgradeInfo.isPro) {
            log(TAG, VERBOSE) { "User is not PRO, skipping anniversary check." }
            return null
        }

        val installDate = LocalDate.ofInstant(installedAt, zone)
        val occurrence = CurriculumVitae.anniversaryOccurrenceOf(installDate, today) ?: return null

        if (dismissedOrdinal == occurrence.ordinal) {
            log(TAG, VERBOSE) { "Anniversary already dismissed for this occurrence." }
            return null
        }

        log(TAG) { "Anniversary detected! $occurrence" }

        val spaceFreed = Formatter.formatShortFileSize(context, statsRepo.state.first().totalSpaceFreed)

        return AnniversaryDashboardCardItem(
            years = occurrence.ordinal,
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
                appScope.launch { generalSettings.anniversaryDismissedOrdinal.value(occurrence.ordinal) }
            },
        )
    }

    companion object {
        private val TAG = logTag("Dashboard", "AnniversaryProvider")
    }
}
