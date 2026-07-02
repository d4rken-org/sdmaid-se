package eu.darken.sdmse.widget

/**
 * Immutable render model for the home-screen widget. The Glance content is a pure function of this.
 */
sealed interface WidgetRenderState {

    data class Data(
        val storages: List<StorageEntry>,
        val freedBytes: Long,
        /** A clean/scan is running: the Clean control turns into a working/cancel affordance. */
        val isWorking: Boolean = false,
        /**
         * The running work can still be cancelled → render a Cancel button. False while working means
         * cancellation is already underway (or nothing is cancellable) → muted "Working…" instead.
         */
        val isCancellable: Boolean = false,
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
