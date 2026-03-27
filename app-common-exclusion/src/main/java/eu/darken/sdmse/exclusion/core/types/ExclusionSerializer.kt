package eu.darken.sdmse.exclusion.core.types

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

object ExclusionSerializer : JsonContentPolymorphicSerializer<Exclusion>(Exclusion::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Exclusion> {
        val keys = element.jsonObject.keys
        return when {
            "pkgId" in keys -> PkgExclusion.serializer()
            "path" in keys -> PathExclusion.serializer()
            "segments" in keys -> SegmentExclusion.serializer()
            else -> throw SerializationException(
                "Unknown Exclusion type. Expected one of: pkgId, path, segments. Got keys: $keys"
            )
        }
    }
}
