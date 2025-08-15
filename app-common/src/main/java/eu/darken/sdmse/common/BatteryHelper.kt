package eu.darken.sdmse.common

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.intervalFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@Reusable
class BatteryHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val powerManager: PowerManager
        get() = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    val isIgnoringBatteryOptimizations = intervalFlow(1.seconds)
        .map { powerManager.isIgnoringBatteryOptimizations(context.packageName) }
        .distinctUntilChanged()
        .onEach { log(TAG) { "isIgnoringBatteryOptimizations=$it" } }

    fun createIntent(): Intent = Intent().apply {
        action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
    }

    companion object {
        private val TAG = logTag("BatteryHelper")
    }
}