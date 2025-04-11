package eu.darken.sdmse.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

/**
 * Gets the underlying file property of a DataStore.
 * This is useful for debugging or when you need to access the raw file.
 *
 * @return The File object representing the DataStore's storage location, or null if it cannot be accessed
 */
val DataStore<Preferences>.file: File?
    get() {
        return try {
            val delegate = this.javaClass
                .getDeclaredField("delegate").apply { isAccessible = true }
                .get(this) ?: return null

            val lazyFile = delegate.javaClass
                .getDeclaredField("file\$delegate").apply { isAccessible = true }
                .get(delegate) ?: return null

            lazyFile.javaClass
                .getDeclaredField("_value").apply { isAccessible = true }
                .get(lazyFile) as? File
        } catch (e: Exception) {
            null
        }
    }

