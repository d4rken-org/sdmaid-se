package eu.darken.sdmse.swiper.core

enum class SwipeDecision {
    UNDECIDED,     // Default - no decision made yet
    KEEP,          // User marked to keep
    DELETE,        // User marked for deletion
    DELETED,       // Successfully deleted
    DELETE_FAILED, // Deletion failed, can retry
}
