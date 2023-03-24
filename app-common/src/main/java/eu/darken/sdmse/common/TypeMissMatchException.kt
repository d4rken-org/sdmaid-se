package eu.darken.sdmse.common

import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

class TypeMissMatchException(private val expected: Any, private val actual: Any) :
    IllegalArgumentException("Type missmatch: Wanted $expected, but got $actual."), HasLocalizedError {

    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = "TypeMissMatchException".toCaString(),
        description = caString { it.getString(R.string.general_error_type_mismatch_msg, expected, actual) }
    )

    companion object {
        fun check(expected: Any, actual: Any) {
            if (expected != actual) throw TypeMissMatchException(expected, actual)
        }
    }
}