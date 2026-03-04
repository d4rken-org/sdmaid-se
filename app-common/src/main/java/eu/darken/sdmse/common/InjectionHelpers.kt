package eu.darken.sdmse.common

import android.app.Service
import dagger.hilt.internal.GeneratedComponentManager

fun Service.isValidAndroidEntryPoint(): Boolean {
    return application is GeneratedComponentManager<*>
}