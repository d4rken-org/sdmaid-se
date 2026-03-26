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
): NavType<T> = if (isNullableAllowed) {
    nullableSerializableNavType(serializer, json)
} else {
    nonNullSerializableNavType(serializer, json)
}

@PublishedApi
internal inline fun <reified T : Any> nonNullSerializableNavType(
    serializer: KSerializer<T>,
    json: Json,
): NavType<T> = object : NavType<T>(isNullableAllowed = false) {
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

/**
 * NavType for nullable route fields. Uses [NavType]<T?> so that [serializeAsValue] and [put]
 * naturally accept null without Kotlin inserting a non-null parameter assertion.
 */
@Suppress("UNCHECKED_CAST")
@PublishedApi
internal inline fun <reified T : Any> nullableSerializableNavType(
    serializer: KSerializer<T>,
    json: Json,
): NavType<T> = object : NavType<T?>(isNullableAllowed = true) {
    override fun put(bundle: Bundle, key: String, value: T?) {
        if (value == null) {
            bundle.remove(key)
        } else {
            bundle.putString(key, json.encodeToString(serializer, value))
        }
    }

    @Suppress("DEPRECATION")
    override fun get(bundle: Bundle, key: String): T? {
        return bundle.getString(key)?.let { json.decodeFromString(serializer, it) }
    }

    override fun parseValue(value: String): T {
        return json.decodeFromString(serializer, Uri.decode(value))
    }

    override fun serializeAsValue(value: T?): String {
        if (value == null) return "null"
        return Uri.encode(json.encodeToString(serializer, value))
    }
} as NavType<T>
