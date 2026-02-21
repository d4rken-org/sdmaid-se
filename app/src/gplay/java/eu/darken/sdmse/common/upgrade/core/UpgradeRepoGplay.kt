package eu.darken.sdmse.common.upgrade.core

import android.app.Activity
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.core.billing.BillingData
import eu.darken.sdmse.common.upgrade.core.billing.BillingManager
import eu.darken.sdmse.common.upgrade.core.billing.PurchasedSku
import eu.darken.sdmse.common.upgrade.core.billing.Sku
import eu.darken.sdmse.common.upgrade.core.billing.SkuDetails
import eu.darken.sdmse.common.upgrade.core.billing.UserCanceledBillingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val billingManager: BillingManager,
    private val billingCache: BillingCache,
) : UpgradeRepo {

    override val storeSite: String = STORE_SITE
    override val upgradeSite: String = UPGRADE_SITE
    override val betaSite: String = BETA_SITE

    override val upgradeInfo: Flow<Info> = billingManager.billingData
        .map<BillingData, BillingData?> { it }
        .onStart { emit(null) }
        .setupCommonEventHandlers(TAG) { "upgradeInfo1" }
        .map { data: BillingData? -> // Only relinquish pro state if we haven't had it for a while
            val now = System.currentTimeMillis()
            val lastProStateAt = billingCache.lastProStateAt.value()
            log(TAG) { "Map: now=$now, lastProStateAt=$lastProStateAt, data=${data}" }

            when {
                data?.purchases?.isNotEmpty() == true -> {
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
        .distinctUntilChanged()
        .retryWhen { error, attempt ->
            // Ignore Google Play errors if the last pro state was recent
            val now = System.currentTimeMillis()
            val lastProStateAt = billingCache.lastProStateAt.value()
            log(TAG) { "Catch: now=$now, lastProStateAt=$lastProStateAt, attempt=$attempt, error=$error" }
            if ((now - lastProStateAt) < 7 * 24 * 60 * 1000L) { // 7 days
                log(TAG, VERBOSE) { "We are not pro, but were recently, and just got an error, what is GPlay doing???" }
                emit(Info(gracePeriod = true, billingData = null))
            } else {
                emit(Info(billingData = null, error = error))
            }
            delay(30_000L * 2.0.pow(attempt.toDouble()).toLong())
            true
        }
        .setupCommonEventHandlers(TAG) { "upgradeInfo2" }
        .shareIn(scope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    fun launchBillingFlow(activity: Activity, sku: Sku, offer: Sku.Subscription.Offer?) {
        log(TAG) { "launchBillingFlow($activity,$sku)" }
        scope.launch {
            try {
                billingManager.startIapFlow(activity, sku, offer)
            } catch (e: Exception) {
                if (e is UserCanceledBillingException) {
                    log(TAG) { "User canceled billing flow" }
                    return@launch
                }
                log(TAG) { "startIapFlow failed:${e.asLog()}" }
                withContext(dispatcherProvider.Main) {
                    e.asErrorDialogBuilder(activity).show()
                }
            }
        }
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = billingManager.querySkus(*skus)

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        billingManager.refresh()
    }

    data class Info(
        private val gracePeriod: Boolean = false,
        private val billingData: BillingData?,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info {

        override val type: UpgradeRepo.Type = UpgradeRepo.Type.GPLAY

        val upgrades: Collection<PurchasedSku> = billingData?.purchases
            ?.map { purchase ->
                purchase.products.mapNotNull { productId ->
                    val sku = OurSku.PRO_SKUS.singleOrNull { it.id == productId }
                    if (sku == null) {
                        log(TAG, ERROR) { "Unknown product: $productId ($purchase)" }
                        return@mapNotNull null
                    } else {
                        log(TAG) { "Mapped $productId to $sku ($purchase)" }
                    }
                    PurchasedSku(sku, purchase)
                }
            }
            ?.flatten()
            ?: emptySet()

        override val isPro: Boolean = upgrades.isNotEmpty() || gracePeriod

        override val upgradedAt: Instant? = upgrades
            .maxByOrNull { it.purchase.purchaseTime }
            ?.let { Instant.ofEpochMilli(it.purchase.purchaseTime) }
    }


    companion object {
        private const val STORE_SITE = "https://play.google.com/store/apps/details?id=eu.darken.sdmse"
        private const val UPGRADE_SITE = "https://play.google.com/store/apps/details?id=eu.darken.sdmse"
        private const val BETA_SITE = "https://play.google.com/apps/testing/eu.darken.sdmse"
        val TAG: String = logTag("Upgrade", "Gplay", "Repo")
    }
}