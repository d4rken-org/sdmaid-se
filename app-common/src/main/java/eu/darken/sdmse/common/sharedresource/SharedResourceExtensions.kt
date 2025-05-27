package eu.darken.sdmse.common.sharedresource

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
suspend fun <T : Any, R> SharedResource<T>.useRes(block: suspend (T) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return get().use { res -> block(res.item) }
}

@OptIn(ExperimentalContracts::class)
suspend inline fun <T : SharedResource<*>, R> Collection<T>.useRes(block: (Collection<*>) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val resources = mutableListOf<Resource<*>>()
    return try {
        forEach { resources.add(it.get()) }
        block(resources.map { it.item })
    } finally {
        resources.forEach { it.close() }
    }
}

suspend inline fun <T : HasSharedResource<*>, R> List<T>.useRes(block: (Collection<*>) -> R): R {
    return map { it.sharedResource }.useRes(block)
}

suspend inline fun <C : HasSharedResource<Any>> C.adoptChildResource(child: HasSharedResource<*>) = apply {
    adoptChildResource(child.sharedResource)
}

suspend inline fun <C : HasSharedResource<*>> C.adoptChildResource(child: SharedResource<*>) = apply {
    sharedResource.addChild(child)
}

fun Collection<KeepAlive>.closeAll() = forEach { it.close() }

suspend inline fun <C : HasSharedResource<*>, R> C.keepResourceHoldersAlive(
    vararg children: HasSharedResource<*>,
    block: () -> R
): R {
    return keepResourcesAlive(
        children = children.map { it.sharedResource }.toTypedArray(),
        block = block,
    )
}

@OptIn(ExperimentalContracts::class)
suspend inline fun <C : HasSharedResource<*>, R> C.keepResourcesAlive(
    vararg children: SharedResource<*>,
    block: () -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val keepAlives = mutableSetOf<Resource<out Any>>()
    return try {
        children
            .map {
                // Keep leases alive through us if we are alive
                adoptChildResource(it)
                // If we are not alive, then we still want to keep a lease until the end of this operation
                it.get()
            }
            .run { keepAlives.addAll(this) }
        block()
    } finally {
        keepAlives.closeAll()
    }
}

suspend fun <T, A : Any> SharedResource<A>.runSessionAction(action: suspend (A) -> T): T = get().use {
    action(it.item)
}