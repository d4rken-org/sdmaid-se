package eu.darken.sdmse.common.serialization

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Format agnostic serializer for the [APath] hierarchy.
 *
 * The written layout is the flat field-set of the concrete subtype (no type discriminator), identical in every format:
 * `{file}` for [LocalPath], `{treeRoot, segments}` for [SAFPath], `{path}` for [RawPath].
 *
 * Reading dispatches on the present fields, so both JSON and non-JSON formats (e.g. the androidx SavedState codec used
 * by the Navigation3 back stack) can restore an [APath].
 */
object APathSerializer : KSerializer<APath> {

    private const val IDX_FILE = 0
    private const val IDX_TREE_ROOT = 1
    private const val IDX_SEGMENTS = 2
    private const val IDX_PATH = 3

    private val segmentsSerializer = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("eu.darken.sdmse.common.files.APath") {
        element("file", String.serializer().descriptor, isOptional = true)
        element("treeRoot", String.serializer().descriptor, isOptional = true)
        element("segments", segmentsSerializer.descriptor, isOptional = true)
        element("path", String.serializer().descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: APath) {
        when (value) {
            is LocalPath -> encoder.encodeSerializableValue(LocalPath.serializer(), value)
            is SAFPath -> encoder.encodeSerializableValue(SAFPath.serializer(), value)
            is RawPath -> encoder.encodeSerializableValue(RawPath.serializer(), value)
            else -> throw SerializationException("Unknown APath type: ${value::class}")
        }
    }

    override fun deserialize(decoder: Decoder): APath = when (decoder) {
        is JsonDecoder -> deserializeJson(decoder)
        else -> deserializeStructured(decoder)
    }

    private fun deserializeJson(decoder: JsonDecoder): APath {
        val element = decoder.decodeJsonElement()
        val keys = element.jsonObject.keys
        // Dispatch on pathType if present (backward compat with old Moshi JSON),
        // fall back to field-name dispatch
        val pathType = element.jsonObject["pathType"]?.jsonPrimitive?.content
        val selected: DeserializationStrategy<APath> = when {
            pathType == "LOCAL" -> LocalPath.serializer()
            pathType == "RAW" -> RawPath.serializer()
            pathType == "SAF" -> SAFPath.serializer()
            "file" in keys -> LocalPath.serializer()
            "treeRoot" in keys -> SAFPath.serializer()
            "path" in keys -> RawPath.serializer()
            else -> throw SerializationException("Unknown APath type, keys: $keys")
        }
        return decoder.json.decodeFromJsonElement(selected, element)
    }

    private fun deserializeStructured(decoder: Decoder): APath = decoder.decodeStructure(descriptor) {
        var file: String? = null
        var treeRoot: String? = null
        var segments: List<String>? = null
        var path: String? = null

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                IDX_FILE -> file = decodeStringElement(descriptor, index)
                IDX_TREE_ROOT -> treeRoot = decodeStringElement(descriptor, index)
                IDX_SEGMENTS -> segments = decodeSerializableElement(descriptor, index, segmentsSerializer)
                IDX_PATH -> path = decodeStringElement(descriptor, index)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected APath element index: $index")
            }
        }

        when {
            file != null && treeRoot == null && segments == null && path == null -> LocalPath(File(file))
            treeRoot != null && segments != null && file == null && path == null -> SAFPath(treeRoot, segments)
            path != null && file == null && treeRoot == null && segments == null -> RawPath(path)
            else -> {
                val keys = listOfNotNull(
                    "file".takeIf { file != null },
                    "treeRoot".takeIf { treeRoot != null },
                    "segments".takeIf { segments != null },
                    "path".takeIf { path != null },
                )
                throw SerializationException("Unknown APath type, keys: $keys")
            }
        }
    }
}
