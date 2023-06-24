package eu.darken.sdmse.automation.core

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.errors.AutomationNoConsentException
import eu.darken.sdmse.automation.core.errors.AutomationNotEnabledException
import eu.darken.sdmse.automation.core.errors.AutomationNotRunningException
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.shareLatest
import eu.darken.sdmse.main.core.GeneralSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val generalSettings: GeneralSettings,
    @AppScope private val appScope: CoroutineScope,
) {

    val useAcs: Flow<Boolean> = generalSettings.hasAcsConsent.flow
        .mapLatest { (it ?: false) && isServiceEnabled() && isServiceLaunched() }
        .shareLatest(appScope)

    fun isServiceEnabled(): Boolean {
        val comp = ComponentName(context, AutomationService::class.java)

        val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)

        while (splitter.hasNext()) {
            val componentNameString = splitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == comp) return true
        }

        return false
    }

    fun isServiceLaunched() = AutomationService.instance != null

    suspend fun submit(task: AutomationTask): AutomationTask.Result {
        log(TAG) { "submit(): $task" }

        if (generalSettings.hasAcsConsent.value() != true) throw AutomationNoConsentException()

        if (!isServiceEnabled()) throw AutomationNotEnabledException()

        val service = AutomationService.instance ?: throw AutomationNotRunningException()
        return service.submit(task)
    }

    companion object {
        val TAG: String = logTag("Automation", "Manager")
    }
}