package eu.darken.sdmse.common.files.core

import java.util.*

typealias Segments = List<String>

fun segs(vararg segment: String): Segments = segment.toList()

fun String.toSegs(seperator: String = "/"): Segments = splitToSequence(seperator).toList()

fun Segments.joinSegments(seperator: String = "/"): String = joinToString(seperator)

fun Segments?.matches(other: Segments?, ignoreCase: Boolean = false): Boolean {
    if (this == null) return other == null
    if (this.size != other?.size) return false
    return indices.all { this[it].equals(other[it], ignoreCase) }
}

fun Segments?.isAncestorOf(other: Segments?, ignoreCase: Boolean = false): Boolean {
    if (this == null || other == null) return false
    if (this.size >= other.size) return false

    return indices.all { this[it].equals(other[it], ignoreCase) }
}

fun Segments?.isParentOf(other: Segments?, ignoreCase: Boolean = false): Boolean {
    if (this == null || other == null) return false
    if (this.size >= other.size) return false

    return isAncestorOf(other, ignoreCase) && matches(other.dropLast(1))
}

fun Segments?.containsSegments(
    other: Segments?,
    ignoreCase: Boolean = false,
    allowPartial: Boolean = false
): Boolean {
    if (this == null || other == null) return false
    if (this.size < other.size) return false
    return if (allowPartial) {
        if (ignoreCase) {
            this.joinSegments().lowercase().contains(other.joinSegments().lowercase())
        } else {
            this.joinSegments().contains(other.joinSegments())
        }
    } else {
        if (ignoreCase) {
            Collections.indexOfSubList(this.map { it.lowercase() }, other.map { it.lowercase() }) != -1
        } else {
            Collections.indexOfSubList(this, other) != -1
        }
    }
}

fun Segments?.startsWith(other: Segments?, ignoreCase: Boolean = false): Boolean {
    if (this == null || other == null) return false
    if (this.size < other.size) return false

    val thisCase = if (ignoreCase) this.map { it.lowercase() } else this
    val otherCase = if (ignoreCase) other.map { it.lowercase() } else other

    return when {
        thisCase.isEmpty() -> {
            otherCase.isEmpty()
        }
        otherCase.size == 1 -> {
            thisCase.first().startsWith(otherCase.first())
        }
        thisCase.size == otherCase.size -> {
            val match = otherCase.dropLast(1) == thisCase.dropLast(1)
            match && thisCase.last().startsWith(otherCase.last())
        }
        else -> {
            val match = otherCase.dropLast(1) == thisCase.dropLast(thisCase.size - otherCase.size + 1)
            match && thisCase[otherCase.size - 1].startsWith(otherCase.last())
        }
    }
}
