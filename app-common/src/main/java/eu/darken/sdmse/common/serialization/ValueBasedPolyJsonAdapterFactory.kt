@file:Suppress("MemberVisibilityCanBePrivate")

package eu.darken.sdmse.common.serialization

import com.squareup.moshi.*
import java.io.IOException
import java.lang.reflect.Type
import java.util.*
import javax.annotation.CheckReturnValue

class ValueBasedPolyJsonAdapterFactory<T> internal constructor(
    val baseType: Class<T>,
    val labelKey: String,
    val labels: List<String>,
    val subtypes: List<Type>,
    val defaultValue: T?,
    val defaultValueSet: Boolean,
    val dontSerializeLabelKey: Boolean = false
) : JsonAdapter.Factory {

    /**
     * Returns a new factory that decodes instances of `subtype`. When an unknown type is found
     * during encoding an [IllegalArgumentException] will be thrown. When an unknown label
     * is found during decoding a [JsonDataException] will be thrown.
     */
    fun withSubtype(subtype: Class<out T>, label: String): ValueBasedPolyJsonAdapterFactory<T> {
        require(!labels.contains(label)) { "Labels must be unique." }
        val newLabels = ArrayList(labels)
        newLabels.add(label)
        val newSubtypes = ArrayList(subtypes)
        newSubtypes.add(subtype)
        return ValueBasedPolyJsonAdapterFactory(
            baseType,
            labelKey,
            newLabels,
            newSubtypes,
            defaultValue,
            defaultValueSet
        )
    }

    fun skipLabelSerialization(): ValueBasedPolyJsonAdapterFactory<T> = ValueBasedPolyJsonAdapterFactory(
        baseType,
        labelKey,
        labels,
        subtypes,
        defaultValue,
        defaultValueSet,
        true
    )

    /**
     * Returns a new factory that with default to `defaultValue` upon decoding of unrecognized
     * labels. The default value should be immutable.
     */
    fun withDefaultValue(defaultValue: T?): ValueBasedPolyJsonAdapterFactory<T> = ValueBasedPolyJsonAdapterFactory(
        baseType,
        labelKey,
        labels,
        subtypes,
        defaultValue,
        true
    )

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
            labelKey,
            labels,
            subtypes,
            jsonAdapters,
            defaultValue,
            defaultValueSet,
            dontSerializeLabelKey
        ).nullSafe()
    }

    internal class PolymorphicJsonAdapter(
        val labelKey: String,
        val labels: List<String>,
        val subtypes: List<Type>,
        val jsonAdapters: List<JsonAdapter<Any>>,
        val defaultValue: Any?,
        val defaultValueSet: Boolean,
        val dontSerializeLabelKey: Boolean
    ) : JsonAdapter<Any>() {

        /** Single-element options containing the label's key only.  */
        val labelKeyOptions: JsonReader.Options = JsonReader.Options.of(labelKey)

        /** Corresponds to subtypes.  */
        val labelOptions: JsonReader.Options = JsonReader.Options.of(*labels.toTypedArray())

        @Throws(IOException::class)
        override fun fromJson(reader: JsonReader): Any? {
            val peeked = reader.peekJson()
            peeked.setFailOnUnknown(false)
            val labelIndex: Int
            try {
                labelIndex = labelIndex(peeked)
            } finally {
                peeked.close()
            }
            if (labelIndex == -1) {
                reader.skipValue()
                return defaultValue
            }
            return jsonAdapters[labelIndex].fromJson(reader)
        }

        @Throws(IOException::class)
        private fun labelIndex(reader: JsonReader): Int {
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.selectName(labelKeyOptions) == -1) {
                    reader.skipName()
                    reader.skipValue()
                    continue
                }

                val labelIndex = reader.selectString(labelOptions)
                if (labelIndex == -1 && !defaultValueSet) {
                    throw JsonDataException(
                        "Expected one of "
                                + labels
                                + " for key '"
                                + labelKey
                                + "' but found '"
                                + reader.nextString()
                                + "'. Register a subtype for this label."
                    )
                }
                return labelIndex
            }

            throw JsonDataException("Missing label for $labelKey")
        }

        @Throws(IOException::class)
        override fun toJson(writer: JsonWriter, value: Any?) {
            requireNotNull(value)
            val type = value.javaClass
            val labelIndex = subtypes.indexOf(type)
            require(labelIndex != -1) {
                ("Expected one of "
                        + subtypes
                        + " but found "
                        + value
                        + ", a "
                        + value.javaClass
                        + ". Register this subtype.")
            }
            val adapter = jsonAdapters[labelIndex]
            writer.beginObject()
            if (!dontSerializeLabelKey) {
                writer.name(labelKey).value(labels[labelIndex])
            }
            val flattenToken = writer.beginFlatten()
            adapter.toJson(writer, value)
            writer.endFlatten(flattenToken)
            writer.endObject()
        }

        override fun toString(): String = "ValueBasedPolyJsonAdapterFactory($labelKey)"
    }

    companion object {

        /**
         * @param baseType The base type for which this factory will create adapters. Cannot be Object.
         * @param labelKey The key in the JSON object whose value determines the type to which to map the
         * JSON object.
         */
        @CheckReturnValue
        fun <T> of(baseType: Class<T>, labelKey: String): ValueBasedPolyJsonAdapterFactory<T> {
            return ValueBasedPolyJsonAdapterFactory(
                baseType,
                labelKey,
                emptyList(),
                emptyList(), null,
                false,
            )
        }
    }
}