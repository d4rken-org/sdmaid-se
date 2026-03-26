package eu.darken.sdmse.common.navigation

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

inline fun <reified T : Any> serializableNavType(
    serializer: KSerializer<T>,
    isNullableAllowed: Boolean = false,
    json: Json = Json { ignoreUnknownKeys = true },
): NavType<T> = object : NavType<T>(isNullableAllowed) {
    override fun put(bundle: Bundle, key: String, value: T) {
        bundle.putString(key, json.encodeToString(serializer, value))
    }

    @Suppress("DEPRECATION")
    override fun get(bundle: Bundle, key: String): T? {
        return bundle.getString(key)?.let { json.decodeFromString(serializer, it) }
    }

    override fun parseValue(value: String): T {
        return json.decodeFromString(serializer, Uri.decode(value))
    }

    override fun serializeAsValue(value: T): String {
        return Uri.encode(json.encodeToString(serializer, value))
    }
}
