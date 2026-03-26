package testhelpers.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val prettyJson = Json { prettyPrint = true }

fun String.toComparableKotlinxJson(): String {
    val element = Json.parseToJsonElement(this)
    return prettyJson.encodeToString(JsonElement.serializer(), element)
}
