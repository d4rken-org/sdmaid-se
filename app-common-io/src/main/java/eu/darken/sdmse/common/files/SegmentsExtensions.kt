package eu.darken.sdmse.common.files

import java.util.Collections

typealias Segments = List<String>

fun segs(vararg segment: String): Segments = segment.toList()

fun String.toSegs(seperator: String = "/"): Segments = splitToSequence(seperator).toList()

fun Segments.joinSegments(seperator: String = "/"): String = joinToString(seperator)

fun Segments.lowercase(): Segments = this.map { it.lowercase() }

fun Segments.prepend(vararg items: String): Segments = items.toList().plus(this)

fun Segments?.matches(other: Segments?, ignoreCase: Boolean = false): Boolean {
    if (this == null) return other == null
    if (this.size != other?.size) return false
    return indices.all { this[it].equals(other[it], ignoreCase) }
}

fun Segments?.isAncestorOf(other: Segments?, ignoreCase: Boolean = false): Boolean {
    if (this == null || other == null) return false
    if (this.size >= other.size) return false

    return other.startsWith(this, ignoreCase = ignoreCase, allowPartial = false)
}

fun Segments?.isParentOf(other: Segments?, ignoreCase: Boolean = false): Boolean {
    if (this == null || other == null) return false
    if (this.size >= other.size) return false

    return isAncestorOf(other, ignoreCase) && matches(other.dropLast(1), ignoreCase = ignoreCase)
}

fun Segments?.isDescendentOf(other: Segments?, ignoreCase: Boolean = false): Boolean {
    if (this == null || other == null) return false
    if (this.size <= other.size) return false

    return startsWith(other, ignoreCase = ignoreCase, allowPartial = false)
}

fun Segments?.isChildOf(other: Segments?, ignoreCase: Boolean = false): Boolean {
    if (this == null || other == null) return false
    if (this.size <= other.size) return false

    return isDescendentOf(other, ignoreCase) && this.dropLast(1).matches(other, ignoreCase = ignoreCase)
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

fun Segments?.startsWith(
    other: Segments?,
    ignoreCase: Boolean = false,
    allowPartial: Boolean = false,
): Boolean {
    if (this == null || other == null) return false
    if (this.size < other.size) return false

    val thisCase = if (ignoreCase) this.map { it.lowercase() } else this
    val otherCase = if (ignoreCase) other.map { it.lowercase() } else other

    return when {
        thisCase.isEmpty() -> {
            otherCase.isEmpty()
        }

        otherCase.size == 1 -> when (allowPartial) {
            true -> thisCase.first().startsWith(otherCase.first())
            false -> thisCase.first() == otherCase.first()
        }

        thisCase.size == otherCase.size -> when (allowPartial) {
            true -> {
                val match = thisCase.dropLast(1) == otherCase.dropLast(1)
                match && thisCase.last().startsWith(otherCase.last())
            }

            false -> thisCase == otherCase
        }

        else -> when (allowPartial) {
            true -> {
                val match = thisCase.dropLast(thisCase.size - otherCase.size + 1) == otherCase.dropLast(1)
                match && thisCase[otherCase.size - 1].startsWith(otherCase.last())
            }

            false -> other.indices.all { this[it].equals(other[it], ignoreCase) }
        }
    }
}

fun Segments?.endsWith(
    other: Segments?,
    ignoreCase: Boolean = false,
    allowPartial: Boolean = false,
): Boolean {
    if (this == null || other == null) return false
    if (this.size < other.size) return false

    val thisCase = if (ignoreCase) this.map { it.lowercase() } else this
    val otherCase = if (ignoreCase) other.map { it.lowercase() } else other

    return when {
        thisCase.isEmpty() -> {
            otherCase.isEmpty()
        }

        otherCase.size == 1 -> when (allowPartial) {
            true -> thisCase.last().endsWith(otherCase.last())
            false -> thisCase.last() == otherCase.last()
        }

        thisCase.size == otherCase.size -> when (allowPartial) {
            true -> {
                // abc/def/ghi <> c/def/ghi
                // def/ghi <> def/ghi
                val match = thisCase.drop(1) == otherCase.drop(1)
                // abc <> c
                match && thisCase.first().endsWith(otherCase.first())
            }

            false -> thisCase == otherCase
        }

        else -> when (allowPartial) {
            true -> {
                // abc/def/ghi <> ef/ghi
                // ghi <> ghi
                val match = thisCase.drop(thisCase.size - otherCase.size + 1) == otherCase.drop(1)
                // def <> ef
                match && thisCase[thisCase.size - otherCase.size].endsWith(otherCase.first())
            }

            false -> thisCase.subList(thisCase.size - otherCase.size, thisCase.size) == otherCase
        }
    }
}