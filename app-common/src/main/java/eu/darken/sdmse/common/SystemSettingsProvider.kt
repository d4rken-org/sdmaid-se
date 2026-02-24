package eu.darken.sdmse.common

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.permissions.Permission
import javax.inject.Inject

@Reusable
class SystemSettingsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    val contentResolver: ContentResolver,
) {

    suspend fun hasSecureWriteAccess(): Boolean = Permission.WRITE_SECURE_SETTINGS.isGranted(context)

    suspend inline fun <reified T : Any> get(type: Type, key: String): T? = when (val valueType = T::class) {
        String::class -> Settings.Secure.getString(contentResolver, key) as T?
        else -> throw UnsupportedOperationException("Type $valueType not supported")
    }.also { log(TAG, VERBOSE) { "get($type,$key) -> $it" } }

    suspend inline fun <reified T : Any> put(type: Type, key: String, value: T) = when (val valueType = T::class) {
        String::class -> Settings.Secure.putString(contentResolver, key, value as String)
        else -> throw UnsupportedOperationException("Type $valueType not supported")
    }.also { log(TAG, VERBOSE) { "put($type,$key,$value) -> $it" } }

    enum class Type(val value: String) {
        SECURE("secure")
    }

    companion object {
        val TAG = logTag("SystemSettings", "Provider")
    }
}