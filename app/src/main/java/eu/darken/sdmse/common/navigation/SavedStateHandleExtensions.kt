package eu.darken.sdmse.common.navigation

import androidx.lifecycle.SavedStateHandle
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <T> SavedStateHandle.mutableState(key: String): ReadWriteProperty<Any?, T?> =
    object : ReadWriteProperty<Any?, T?> {
        private var field: T? = null

        override fun getValue(thisRef: Any?, property: KProperty<*>): T? = field ?: get(key)

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
            field = value.also { set(key, it) }
        }
    }
