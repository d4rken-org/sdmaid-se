package eu.darken.sdmse.swiper.core

enum class SessionState {
    CREATED,   // Session created, paths selected, not yet scanned
    READY,     // Scanned, items populated, ready for swiping
    COMPLETED, // All done, ready for cleanup
}
