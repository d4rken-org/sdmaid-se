package eu.darken.sdmse.common

import android.app.Service
import android.content.Context
import dagger.hilt.internal.GeneratedComponentManager
import eu.darken.sdmse.App

fun Context.isValidHiltContext(): Boolean {
    return applicationContext is GeneratedComponentManager<*> || applicationContext is App
}

fun Service.isValidAndroidEntryPoint(): Boolean = (this as Context).isValidHiltContext()