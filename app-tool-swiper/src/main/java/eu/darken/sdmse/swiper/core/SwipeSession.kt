package eu.darken.sdmse.swiper.core

import eu.darken.sdmse.common.files.APath
import java.time.Instant

data class SwipeSession(
    val sessionId: String,
    val sourcePaths: List<APath>,
    val currentIndex: Int,
    val totalItems: Int,
    val createdAt: Instant,
    val lastModifiedAt: Instant,
    val state: SessionState,
    val label: String? = null,
    val keptCount: Int = 0,
    val deletedCount: Int = 0,
)
