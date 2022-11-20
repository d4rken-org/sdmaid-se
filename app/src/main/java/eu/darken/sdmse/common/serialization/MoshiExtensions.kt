package eu.darken.sdmse.common.serialization

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import okio.Buffer
import okio.BufferedSource

inline fun <reified T> Moshi.fromJson(json: String): T =
    fromJson(Buffer().writeUtf8(json))

inline fun <reified T> Moshi.fromJson(source: BufferedSource): T =
    fromJson(JsonReader.of(source))

inline fun <reified T> Moshi.fromJson(reader: JsonReader): T =
    adapter<T>().fromJson(reader) ?: throw JsonDataException()

inline fun <reified T> Moshi.toJson(value: T): String =
    adapter<T>().toJson(value)