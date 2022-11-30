package eu.darken.sdmse.common.sharedresource

interface HasSharedResource<T : Any> {
    val sharedResource: SharedResource<T>

    suspend fun addParent(parent: SharedResource<*>) {
        sharedResource.addParent(parent)
    }

    suspend fun <C : HasSharedResource<T>> C.addParent(parent: HasSharedResource<*>) = apply {
        sharedResource.addParent(parent.sharedResource)
    }

    suspend fun <R> use(block: suspend (T) -> R): R {
        return sharedResource.get().use { block(it.item) }
    }
}