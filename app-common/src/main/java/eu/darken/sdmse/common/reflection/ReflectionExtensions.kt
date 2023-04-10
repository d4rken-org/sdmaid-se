package eu.darken.sdmse.common.reflection

import java.lang.reflect.Field
import kotlin.reflect.KClass

fun <T : Any> T.accessField(fieldName: String): Any? = javaClass.getDeclaredField(fieldName).let { field ->
    field.isAccessible = true
    return@let field.get(this)
}

fun <T : Any> KClass<T>.getField(fieldName: String): Field = java.getDeclaredField(fieldName).apply {
    isAccessible = true
}