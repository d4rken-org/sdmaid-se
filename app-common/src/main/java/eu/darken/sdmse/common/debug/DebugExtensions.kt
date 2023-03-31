package eu.darken.sdmse.common.debug

import eu.darken.sdmse.common.error.getStackTraceString


@Suppress("NOTHING_TO_INLINE")
inline fun traceCall() = CallTrace().getStackTraceString()

class CallTrace : Throwable()