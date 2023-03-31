package eu.darken.sdmse.common.sharedresource


suspend fun <T : Any, R> SharedResource<T>.useRes(block: suspend (T) -> R): R = get().use { res ->
    block(res.item)
}

suspend inline fun <C : HasSharedResource<Any>> C.adoptChildResource(child: HasSharedResource<*>) = apply {
    adoptChildResource(child.sharedResource)
}

suspend inline fun <C : HasSharedResource<*>> C.adoptChildResource(child: SharedResource<*>) = apply {
    sharedResource.addChild(child)
}

suspend fun <C : HasSharedResource<*>> Collection<C>.getAllResources() = map { it.sharedResource.get() }

fun Collection<KeepAlive>.closeAll() = forEach { it.close() }

suspend inline fun <C : HasSharedResource<*>, R> C.keepResourceHoldersAlive(
    children: Collection<HasSharedResource<*>>,
    block: () -> R
): R {
    return keepResourcesAlive(children.map { it.sharedResource }, block)
}

suspend inline fun <C : HasSharedResource<*>, R> C.keepResourcesAlive(
    children: Collection<SharedResource<*>>,
    block: () -> R
): R {
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