package eu.darken.sdmse.automation.core

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationController @Inject constructor(
    @ApplicationContext val context: Context
) {

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
        val service = AutomationService.instance ?: throw IllegalStateException("Accessbility service unavailable")
        return service.submit(task)
    }

    companion object {
        val TAG: String = logTag("Automation", "Controller")
    }
}