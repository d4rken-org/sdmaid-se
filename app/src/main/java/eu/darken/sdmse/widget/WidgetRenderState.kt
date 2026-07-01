package eu.darken.sdmse.widget

/**
 * Immutable render model for the home-screen widget. The Glance content is a pure function of this.
 */
sealed interface WidgetRenderState {

    data class Data(
        val storages: List<StorageEntry>,
        val freedBytes: Long,
    ) : WidgetRenderState {

        data class StorageEntry(
            val kind: Kind,
            val usedBytes: Long,
            val totalBytes: Long,
        ) {
            val usedRatio: Float
                get() = if (totalBytes > 0L) {
                    (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

            enum class Kind { INTERNAL, EXTERNAL }
        }
    }

    /** No storage could be read (read failure or no volume with positive capacity). */
    data object Unavailable : WidgetRenderState
}
