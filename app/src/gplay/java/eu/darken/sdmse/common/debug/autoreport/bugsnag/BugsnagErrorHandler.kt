package eu.darken.sdmse.common.debug.autoreport.bugsnag

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import com.bugsnag.android.Event
import com.bugsnag.android.OnErrorCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.autoreport.DebugSettings
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.main.core.GeneralSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BugsnagErrorHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bugsnagLogger: BugsnagLogger,
    private val generalSettings: GeneralSettings,
    private val debugSettings: DebugSettings,
    private val dataAreaManager: DataAreaManager,
) : OnErrorCallback {

    override fun onError(event: Event): Boolean {

        TAB_APP.let { tab ->
            event.addMetadata(tab, "flavor", BuildConfigWrap.FLAVOR.toString())
            event.addMetadata(tab, "buildType", BuildConfigWrap.BUILD_TYPE.toString())

            event.addMetadata(tab, "gitSha", BuildConfigWrap.GIT_SHA)
            event.addMetadata(tab, "locales", "${Resources.getSystem().configuration.locales}")

            context.tryFormattedSignature()?.let { event.addMetadata(tab, "signatures", it) }
        }

        TAB_DATA_AREAS.let { tab ->
            runBlocking { dataAreaManager.latestState.first() }
                ?.areas
                ?.groupBy { it.type }
                ?.forEach { (type, areas) ->
                    areas.forEachIndexed { index, area ->
                        val key = "${type.raw} #$index"
                        val row = "${area.path} | ${area.userHandle.handleId} | ${area.flags}"

                        event.addMetadata(tab, key, row)
                    }
                }
        }

        bugsnagLogger.injectLog(event)

        return generalSettings.isBugReporterEnabled.valueBlocking && !BuildConfigWrap.DEBUG
    }

    companion object {
        private const val TAB_APP = "app"
        private const val TAB_DATA_AREAS = "data areas"

        @Suppress("DEPRECATION")
        @SuppressLint("PackageManagerGetSignatures")
        fun Context.tryFormattedSignature(): String? = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures?.let { sigs ->
                val sb = StringBuilder("[")
                for (i in sigs.indices) {
                    sb.append(sigs[i].hashCode())
                    if (i + 1 != sigs.size) sb.append(", ")
                }
                sb.append("]")
                sb.toString()
            }
        } catch (e: Exception) {
            log(WARN) { e.asLog() }
            null
        }
    }

}