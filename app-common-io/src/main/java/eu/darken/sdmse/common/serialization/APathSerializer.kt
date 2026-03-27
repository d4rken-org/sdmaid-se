package eu.darken.sdmse.common.serialization

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object APathSerializer : JsonContentPolymorphicSerializer<APath>(APath::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<APath> {
        val keys = element.jsonObject.keys
        // Dispatch on pathType if present (backward compat with old Moshi JSON),
        // fall back to field-name dispatch
        val pathType = element.jsonObject["pathType"]?.jsonPrimitive?.content
        return when {
            pathType == "LOCAL" -> LocalPath.serializer()
            pathType == "RAW" -> RawPath.serializer()
            pathType == "SAF" -> SAFPath.serializer()
            "file" in keys -> LocalPath.serializer()
            "treeRoot" in keys -> SAFPath.serializer()
            "path" in keys -> RawPath.serializer()
            else -> throw SerializationException("Unknown APath type, keys: $keys")
        }
    }
}
