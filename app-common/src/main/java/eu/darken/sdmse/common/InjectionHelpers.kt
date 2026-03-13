package eu.darken.sdmse.common

import android.app.Service
import android.content.Context
import dagger.hilt.internal.GeneratedComponentManager

fun Context.isValidHiltContext(): Boolean {
    return applicationContext is GeneratedComponentManager<*>
}

fun Service.isValidAndroidEntryPoint(): Boolean = (this as Context).isValidHiltContext()
