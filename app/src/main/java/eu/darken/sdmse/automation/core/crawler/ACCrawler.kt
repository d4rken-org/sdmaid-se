package eu.darken.sdmse.automation.core.crawler

import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.automation.core.AutomationSupportException
import eu.darken.sdmse.automation.core.SpecSource
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.features.Installed

class ACCrawler(private val host: AutomationHost) {

//    fun crawl(spec: Spec): Single<Result> {
//        var retryCount = 0
//        return Single
//            .fromCallable {
//                Timber.tag(TAG).d("Looking for window root (intent=%s).", spec.windowIntent)
//                if (retryCount > 0 && !ApiHelper.hasAndroid12()) {
//                    Timber.tag(TAG).d("Clearing system dialogs (retryCount=%d).", retryCount)
//                    val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
//                    try {
//                        host.getService().sendBroadcast(closeIntent)
//                    } catch (e: Exception) {
//                        Timber.tag(TAG).w(e, "Sending ACTION_CLOSE_SYSTEM_DIALOGS failed")
//                    }
//                }
//                retryCount++
//                if (spec.windowIntent != null) host.getService().startActivity(spec.windowIntent)
//                true
//            }
//            .subscribeOn(AndroidSchedulers.mainThread()).observeOn(Schedulers.io())
//            .delay(200, TimeUnit.MILLISECONDS) // avg delay between activity launch and acs event
//            .flatMap {
//                val rootLookup = if (spec.windowIntent == null) {
//                    host.windowRoot()
//                } else {
//                    host.events()
//                        .filter { spec.windowEventFilter == null || spec.windowEventFilter.invoke(it) }
//                        .firstOrError()
//                        .flatMap { host.windowRoot() }
//                }
//                return@flatMap rootLookup
//                    .map { rootNode ->
//                        if (spec.windowNodeTest == null || spec.windowNodeTest.invoke(rootNode)) {
//                            return@map rootNode
//                        } else {
//                            throw CrawlerException("Not a viable root window: $rootNode (spec=$spec)")
//                        }
//                    }
//                    .doOnError { Timber.tag(TAG).d("No valid root-node found: %s", it.toString()) }
//                    .retryDelayed(5, 100, retryCondition = { !host.isCanceled() && it !is BranchException }) // 3400ms
//            }
//            .timeout(4000, TimeUnit.MILLISECONDS)
//            .doOnSuccess { Timber.tag(TAG).d("Found root-node: $it") }
//            .flatMap {
//                return@flatMap host.windowRoot() // Get new root for retries
//                    .doOnSuccess { Timber.tag(TAG).v("Current root: %s", it.toStringShort()) }
//                    .map { root ->
//                        if (spec.nodeTest == null) return@map root
//                        var target = root.crawl().map { it.node }.find { spec.nodeTest.invoke(it) }
//                        if (target == null && spec.nodeRecovery != null) {
//                            // Should we care about whether the recovery thinks it was successful?
//                            spec.nodeRecovery.invoke(root)
//                            target = host.windowRoot().blockingGet().crawl().map { it.node }
//                                .find { spec.nodeTest.invoke(it) }
//                        }
//                        return@map target ?: throw CrawlerException("No matching node found for $spec")
//                    }
//                    .retryDelayed(5, 100, retryCondition = { !host.isCanceled() && it !is BranchException })
//            }
//            .map { spec.nodeMapping?.invoke(it) ?: it }
//            .map { node ->
//                val success = spec.action?.invoke(node, retryCount) ?: true
//                if (success) return@map node else throw CrawlerException("Action failed on $node (spec=$spec)")
//            }
//            .doOnError { Timber.tag(TAG).d("Failed $spec, retrying: %s", it.toString()) }
//            .retryDelayed(10, 50) { !host.isCanceled() && it !is BranchException }
//            .timeout(20, TimeUnit.SECONDS)
//            .map { Result(true) }
//            .doOnSuccess { Timber.tag(TAG).d("crawl($spec) - result: %s", it) }
//            .doOnError { Timber.tag(TAG).e(it, "crawl($spec) - error") }
//            .onErrorReturn {
//                Result(
//                    false,
//                    CrawlerException("Error during $spec (isCanceled=${host.isCanceled()})", it)
//                )
//            }
//    }

    companion object {
        internal val TAG: String = logTag("Automation", "Crawler")
    }


    data class Spec(
        val parentTag: String,
        val pkgInfo: Installed,
        val label: String,
        val isHailMary: Boolean = false,
        val windowIntent: Intent? = null,
        val windowEventFilter: ((node: AccessibilityEvent) -> Boolean)? = null,
        val windowNodeTest: ((node: AccessibilityNodeInfo) -> Boolean)? = null,
        val nodeTest: ((node: AccessibilityNodeInfo) -> Boolean)? = null,
        val nodeRecovery: ((node: AccessibilityNodeInfo) -> Boolean)? = null,
        val nodeMapping: ((node: AccessibilityNodeInfo) -> AccessibilityNodeInfo)? = null,
        val action: ((node: AccessibilityNodeInfo, retryCount: Int) -> Boolean)? = null
    ) {

        fun createHailMaryException(cause: Throwable): Throwable {
            val locale = SpecSource.getSysLocale()
            val apiLevel = BuildWrap.VERSION.SDK_INT
            val rom = Build.MANUFACTURER
            return AutomationSupportException(
                message = "Spec(ROM=$rom, API=$apiLevel, LOCALE=$locale) failed: $label",
                cause = cause
            )
        }

        override fun toString(): String = "Spec(parent=$parentTag, label=$label, pkg=${pkgInfo.packageName})"
    }

    data class Result(val success: Boolean, val exception: Exception? = null)
}