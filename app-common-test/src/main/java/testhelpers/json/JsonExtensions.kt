package testhelpers.json

import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import okio.Buffer
import okio.ByteString.Companion.encode
import okio.buffer
import okio.sink
import java.io.File


fun String.toComparableJson(): String {
    val value = Buffer().use {
        it.writeUtf8(this)
        val reader = JsonReader.of(it)
        reader.readJsonValue()
    }

    val adapter = Moshi.Builder().build().adapter(Any::class.java).indent("    ")

    return adapter.toJson(value)
}

fun String.writeToFile(file: File) = encode().let { text ->
    require(!file.exists())
    file.parentFile?.mkdirs()
    file.createNewFile()
    file.sink().buffer().use { it.write(text) }
}