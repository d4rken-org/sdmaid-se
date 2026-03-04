package eu.darken.sdmse.automation.core

import android.content.Context
import android.content.Intent

fun interface AutomationReturnHelper {
    fun createReturnToAppIntent(context: Context): Intent
}
