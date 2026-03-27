package testhelpers.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.ByteString.Companion.encode
import okio.buffer
import okio.sink
import java.io.File

private val prettyJson = Json { prettyPrint = true }

fun String.toComparableJson(): String {
    val element = Json.parseToJsonElement(this.trim())
    return prettyJson.encodeToString(JsonElement.serializer(), element)
}

fun String.writeToFile(file: File) = encode().let { text ->
    require(!file.exists())
    file.parentFile?.mkdirs()
    file.createNewFile()
    file.sink().buffer().use { it.write(text) }
}
