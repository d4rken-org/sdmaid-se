package eu.darken.sdmse.common.upgrade.core

import android.app.Activity
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.core.data.BillingData
import eu.darken.sdmse.common.upgrade.core.data.BillingDataRepo
import eu.darken.sdmse.common.upgrade.core.data.PurchasedSku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val billingDataRepo: BillingDataRepo,
    private val billingCache: BillingCache,
) : UpgradeRepo {

    override val mainWebsite: String = SITE

    override val upgradeInfo: Flow<UpgradeRepo.Info> = billingDataRepo.billingData
        .map { data -> // Only relinquish pro state if we haven't had it for a while
            val now = System.currentTimeMillis()
            val proSku = data.getProSku()
            val lastProStateAt = billingCache.lastProStateAt.value()
            log(TAG) { "now=$now, lastProStateAt=$lastProStateAt, data=${data}" }
            when {
                proSku != null -> {
                    // If we are pro refresh timestamp
                    billingCache.lastProStateAt.value(now)
                    Info(billingData = data)
                }
                (now - lastProStateAt) < 7 * 24 * 60 * 1000L -> { // 7 days
                    log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
                    Info(gracePeriod = true, billingData = null)
                }
                else -> {
                    Info(billingData = data)
                }
            }
        }
        .catch {
            // Ignore Google Play errors if the last pro state was recent
            val now = System.currentTimeMillis()
            val lastProStateAt = billingCache.lastProStateAt.value()
            log(TAG) { "now=$now, lastProStateAt=$lastProStateAt, error=$it" }
            if ((now - lastProStateAt) < 7 * 24 * 60 * 1000L) { // 7 days
                log(TAG, VERBOSE) { "We are not pro, but were recently, and just and an error, what is GPlay doing???" }
                emit(Info(gracePeriod = true, billingData = null))
            } else {
                throw it
            }
        }
        .replayingShare(scope)

    fun launchBillingFlowSubscription(activity: Activity) {
        scope.launch {
            try {
                billingDataRepo.startIapFlow(activity, SDMaidSESKU.PRO_UPGRADE.sku)
            } catch (e: Exception) {
                log(TAG) { "startIapFlow failed:${e.asLog()}" }
                withContext(dispatcherProvider.Main) {
                    e.asErrorDialogBuilder(activity).show()
                }
            }
        }
    }

    fun launchBillingFlowIap(activity: Activity) {
        scope.launch {
            try {
                billingDataRepo.startIapFlow(activity, SDMaidSESKU.PRO_UPGRADE.sku)
            } catch (e: Exception) {
                log(TAG) { "startIapFlow failed:${e.asLog()}" }
                withContext(dispatcherProvider.Main) {
                    e.asErrorDialogBuilder(activity).show()
                }
            }
        }
    }

    data class Info(
        private val gracePeriod: Boolean = false,
        private val billingData: BillingData?,
    ) : UpgradeRepo.Info {

        override val type: UpgradeRepo.Type
            get() = UpgradeRepo.Type.GPLAY

        override val isPro: Boolean
            get() = billingData?.getProSku() != null

        override val upgradedAt: Instant?
            get() = billingData
                ?.getProSku()
                ?.purchase?.purchaseTime
                ?.let { Instant.ofEpochMilli(it) }
    }

    companion object {
        private fun BillingData.getProSku(): PurchasedSku? = purchasedSkus
            .firstOrNull { it.sku == SDMaidSESKU.PRO_UPGRADE.sku }

        private const val SITE = "https://play.google.com/store/apps/details?id=eu.darken.sdmse"
        val TAG: String = logTag("Upgrade", "Gplay", "Control")
    }
}