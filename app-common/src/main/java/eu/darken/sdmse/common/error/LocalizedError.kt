package eu.darken.sdmse.common.error

import android.app.Activity
import android.content.Context
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString

interface HasLocalizedError {
    fun getLocalizedError(): LocalizedError
}

data class LocalizedError(
    val throwable: Throwable,
    val label: CaString,
    val description: CaString,
    val fixActionLabel: CaString? = null,
    val fixAction: ((Activity) -> Unit)? = null,
    val infoAction: ((Activity) -> Unit)? = null,
) {
    fun asText() = caString { "${label.get(it)}:\n${description.get(it)}" }
}

fun Throwable.localized(c: Context): LocalizedError = when {
    this is HasLocalizedError -> this.getLocalizedError()
    localizedMessage != null -> LocalizedError(
        throwable = this,
        label = caString { "${c.getString(R.string.general_error_label)} - ${this::class.simpleName!!}" },
        description = caString { localizedMessage ?: getStackTracePeek() }
    )

    else -> LocalizedError(
        throwable = this,
        label = caString { "${c.getString(R.string.general_error_label)} - ${this::class.simpleName!!}" },
        description = caString { getStackTracePeek() }
    )
}

private fun Throwable.getStackTracePeek() = this.stackTraceToString()
    .lines()
    .filterIndexed { index, _ -> index > 1 }
    .take(3)
    .joinToString("\n")