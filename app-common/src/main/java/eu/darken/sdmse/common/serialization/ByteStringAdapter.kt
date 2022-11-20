package eu.darken.sdmse.common.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

class ByteStringAdapter {
    @ToJson
    fun toJson(value: ByteString): String = value.base64()

    @FromJson
    fun fromJson(raw: String): ByteString = raw.decodeBase64()!!
}