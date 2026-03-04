package eu.darken.sdmse.appcontrol.core.usage

import android.app.usage.UsageStatsManager
import dagger.Reusable
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import java.util.Calendar
import javax.inject.Inject

@Reusable
class UsageTool @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val statsManager: UsageStatsManager,
    private val userManager2: UserManager2,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    suspend fun lastMonth(): Set<UsageInfo> {
        log(TAG, DEBUG) { "lastMonth()" }
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        val stats = statsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_MONTHLY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        )
        log(TAG, VERBOSE) { "${stats.size} stats for last month" }
        // TODO how do we get infos for other users?
        val currentUser = userManager2.currentUser()
        return stats
            .groupBy { it.packageName }
            .map { entry ->
                UsageInfo(
                    installId = InstallId(
                        pkgId = entry.key.toPkgId(),
                        userHandle = currentUser.handle,
                    ),
                    stats = entry.value,
                )
            }
            .toSet()
    }

    companion object {
        private val TAG = logTag("AppControl", "UsageTool")
    }
}