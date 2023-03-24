package eu.darken.sdmse.common.sharedresource

data class Resource<T>(private val _item: T, val keepAlive: KeepAlive) : KeepAlive by keepAlive {
    val item: T
        get() {
            check(!keepAlive.isClosed) { "Trying to access closed resource! ($_item, $keepAlive)" }
            return _item
        }
}