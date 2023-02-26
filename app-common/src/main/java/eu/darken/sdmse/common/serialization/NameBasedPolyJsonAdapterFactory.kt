@file:Suppress("MemberVisibilityCanBePrivate")

package eu.darken.sdmse.common.serialization

import com.squareup.moshi.*
import java.lang.reflect.Type
import java.util.*
import javax.annotation.CheckReturnValue

class NameBasedPolyJsonAdapterFactory<T> internal constructor(
    val baseType: Class<T>,
    val keyLabels: List<String> = emptyList(),
    val subtypes: List<Type> = emptyList(),
) : JsonAdapter.Factory {

    fun withSubtype(subtype: Class<out T>, label: String): NameBasedPolyJsonAdapterFactory<T> {
        require(!keyLabels.contains(label)) { "Labels must be unique." }
        return NameBasedPolyJsonAdapterFactory(
            baseType = baseType,
            keyLabels = keyLabels + label,
            subtypes = subtypes + subtype,
        )
    }

    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (Types.getRawType(type) != baseType || annotations.isNotEmpty()) {
            return null
        }

        val jsonAdapters = ArrayList<JsonAdapter<Any>>(subtypes.size)
        var i = 0
        val size = subtypes.size
        while (i < size) {
            jsonAdapters.add(moshi.adapter(subtypes[i]))
            i++
        }

        return PolymorphicJsonAdapter(
            nameLabels = keyLabels,
            subTypes = subtypes,
            jsonAdapters = jsonAdapters,
        ).nullSafe()
    }

    internal class PolymorphicJsonAdapter(
        val nameLabels: List<String>,
        val subTypes: List<Type>,
        val jsonAdapters: List<JsonAdapter<Any>>,
    ) : JsonAdapter<Any>() {

        private val nameOptions: JsonReader.Options = JsonReader.Options.of(*nameLabels.toTypedArray())

        override fun fromJson(reader: JsonReader): Any? {
            val peeked = reader.peekJson().apply {
                setFailOnUnknown(false)
            }
            val labelIndex = peeked.use(::labelIndex)

            if (labelIndex == -1) {
                throw JsonDataException("No matching Field names for $nameLabels")
            }

            return jsonAdapters[labelIndex].fromJson(reader)

        }

        private fun labelIndex(reader: JsonReader): Int {
            reader.beginObject()
            while (reader.hasNext()) {
                val labelIndex = reader.selectName(nameOptions)
                if (labelIndex == -1) {
                    reader.skipName()
                    reader.skipValue()
                    continue
                }
                return labelIndex
            }

            return -1
//            throw JsonDataException("Missing label for $labelKey")
        }

        override fun toJson(writer: JsonWriter, value: Any?) {
            val type = value!!.javaClass
            val typeIndex = subTypes.indexOf(type)

            if (typeIndex == -1) {
                throw JsonDataException("No matching name label for $value. Valid labels are $nameLabels")
            }

            val adapter = jsonAdapters[typeIndex]

            writer.beginObject()
            val flattenToken = writer.beginFlatten()
            adapter.toJson(writer, value)
            writer.endFlatten(flattenToken)
            writer.endObject()
        }

        override fun toString(): String = "KeyBasedPolyJsonAdapterFactory($nameLabels)"
    }

    companion object {

        @CheckReturnValue
        fun <T> of(baseType: Class<T>): NameBasedPolyJsonAdapterFactory<T> = NameBasedPolyJsonAdapterFactory(
            baseType
        )
    }
}