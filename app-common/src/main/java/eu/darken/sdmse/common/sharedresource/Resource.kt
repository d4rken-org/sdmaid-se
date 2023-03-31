package eu.darken.sdmse.common.sharedresource

import kotlin.coroutines.cancellation.CancellationException

data class Resource<T>(
    private val _item: T,
    val lease: SharedResource<*>.ActiveLease,
) : KeepAlive by lease {
    val item: T
        get() {
            if (lease.job.isCancelled) throw CancellationException("Resource was cancelled! ($_item, $lease)")
            if (lease.isClosed) throw IllegalAccessException("Trying to access closed resource! ($_item, $lease)")
            return _item
        }
}