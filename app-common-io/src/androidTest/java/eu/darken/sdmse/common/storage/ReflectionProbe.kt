package eu.darken.sdmse.common.storage

import java.lang.reflect.InvocationTargetException

/**
 * The wrappers under test collapse "member is not reachable" and "call failed" into the same null,
 * so both have to be observed separately, right next to the wrapper call.
 */
internal sealed interface ProbeResult {
    /** Hidden-API enforcement hides blocked members from [Class.getMethod], so this is how a block shows up. */
    data class NoSuchMember(val error: Throwable) : ProbeResult

    data class Threw(val cause: Throwable) : ProbeResult

    data class Returned(val value: Any?) : ProbeResult

    val reachable: Boolean
        get() = this !is NoSuchMember
}

internal fun Any.probe(
    name: String,
    params: List<Class<*>> = emptyList(),
    args: List<Any?> = emptyList(),
): ProbeResult {
    val method = try {
        javaClass.getMethod(name, *params.toTypedArray())
    } catch (e: Throwable) {
        return ProbeResult.NoSuchMember(e)
    }
    return try {
        ProbeResult.Returned(method.invoke(this, *args.toTypedArray()))
    } catch (e: InvocationTargetException) {
        ProbeResult.Threw(e.cause ?: e)
    } catch (e: Throwable) {
        ProbeResult.Threw(e)
    }
}
