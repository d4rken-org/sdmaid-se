package eu.darken.sdmse.common

import android.app.Service
import dagger.hilt.internal.GeneratedComponentManager
import eu.darken.sdmse.App

fun Service.isValidAndroidEntryPoint(): Boolean {
    return application is GeneratedComponentManager<*> || application is App
}